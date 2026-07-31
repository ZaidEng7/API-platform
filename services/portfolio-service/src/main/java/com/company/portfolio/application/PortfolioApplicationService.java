package com.company.portfolio.application;

import com.company.platform.messaging.envelope.EventEnvelope;
import com.company.platform.messaging.outbox.OutboxEventStore;
import com.company.platform.security.CurrentUser;
import com.company.platform.web.correlation.CorrelationIdFilter;
import com.company.platform.web.exception.ApiException;
import com.company.portfolio.api.dto.PortfolioValuationResponse;
import com.company.portfolio.api.dto.PositionValuation;
import com.company.portfolio.domain.Portfolio;
import com.company.portfolio.domain.Position;
import com.company.portfolio.domain.event.PortfolioOpened;
import com.company.portfolio.domain.event.PositionRecorded;
import com.company.portfolio.infrastructure.PortfolioJpaRepository;
import com.company.portfolio.infrastructure.PositionJpaRepository;
import com.company.portfolio.infrastructure.client.FundNavClient;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Enforces guide §12.2's object-level authorization rule: "an Investor
 * sees *their* portfolio only... every {@code GET /portfolios/{id}} must
 * verify ownership — this is the #1 API vulnerability class (BOLA/IDOR)."
 * {@link com.company.platform.security.CurrentUser}'s own Javadoc already
 * documented this exact rule as the reason it exists; this is the first
 * service to actually use it for that purpose (KYC/AML use it only to
 * attribute a decision, not to gate access).
 */
@Service
public class PortfolioApplicationService {

    private static final List<String> STAFF_ROLES =
            List.of("OPERATIONS", "PORTFOLIO_MANAGER", "AUDITOR", "COMPLIANCE");

    private static final String PORTFOLIO_OPENED_EVENT_TYPE = "portfolio.account.opened";
    private static final String POSITION_RECORDED_EVENT_TYPE = "portfolio.position.recorded";
    private static final String PRODUCER = "portfolio-service";

    private final PortfolioJpaRepository portfolioRepository;
    private final PositionJpaRepository positionRepository;
    private final FundNavClient fundNavClient;
    private final OutboxEventStore outboxEventStore;

    public PortfolioApplicationService(PortfolioJpaRepository portfolioRepository,
                                        PositionJpaRepository positionRepository, FundNavClient fundNavClient,
                                        OutboxEventStore outboxEventStore) {
        this.portfolioRepository = portfolioRepository;
        this.positionRepository = positionRepository;
        this.fundNavClient = fundNavClient;
        this.outboxEventStore = outboxEventStore;
    }

    @Transactional(readOnly = true)
    public Portfolio getById(UUID id) {
        Portfolio portfolio = portfolioRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PORTFOLIO-4041", "Portfolio not found: " + id));
        enforceOwnership(portfolio);
        return portfolio;
    }

    /** @throws ApiException 403 if an investor-only caller queries another investor's {@code ownerId}. */
    @Transactional(readOnly = true)
    public Page<Portfolio> listByOwner(UUID ownerId, Pageable pageable) {
        if (!isStaff() && !ownerId.toString().equals(CurrentUser.subject().orElse(null))) {
            throw new ApiException(HttpStatus.FORBIDDEN, "PORTFOLIO-4030", "You do not have access to that owner's portfolios");
        }
        return portfolioRepository.findByOwnerId(ownerId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Position> listPositions(UUID portfolioId, Pageable pageable) {
        getById(portfolioId); // 404s if unknown, 403s if not owned (when caller is investor-only)
        return positionRepository.findByPortfolioId(portfolioId, pageable);
    }

    /**
     * Calls Fund Service for each distinct fund's latest NAV and computes
     * market value — no FX conversion (see {@link PortfolioValuationResponse}'s
     * Javadoc). If any fund's NAV can't be obtained, the whole valuation
     * fails rather than silently omitting a position — a partial
     * valuation would be misleading, not merely incomplete.
     */
    @Transactional(readOnly = true)
    public PortfolioValuationResponse valuate(UUID portfolioId) {
        Portfolio portfolio = getById(portfolioId);
        List<Position> positions = positionRepository.findByPortfolioId(portfolioId);

        Map<String, BigDecimal> navByFundCode = new LinkedHashMap<>();
        for (Position position : positions) {
            navByFundCode.computeIfAbsent(position.getFundCode(),
                    fundCode -> fundNavClient.getNav(fundCode).navPerShare());
        }

        List<PositionValuation> valuations = positions.stream()
                .map(position -> {
                    BigDecimal nav = navByFundCode.get(position.getFundCode());
                    return new PositionValuation(position.getFundCode(), position.getQuantity(), nav,
                            nav.multiply(position.getQuantity()));
                })
                .toList();

        BigDecimal total = valuations.stream()
                .map(PositionValuation::marketValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new PortfolioValuationResponse(portfolio.getId(), portfolio.getCurrency(), valuations, total,
                Instant.now());
    }

    /**
     * Writes the outbox row in the SAME transaction as the portfolio
     * insert (guide §8.4: no dual-write without outbox).
     */
    @Transactional
    public Portfolio openPortfolio(UUID customerId, UUID ownerId, String name, String currency) {
        Instant now = Instant.now();
        Portfolio portfolio = portfolioRepository.save(
                new Portfolio(UUID.randomUUID(), customerId, ownerId, name, currency, now));

        var payload = new PortfolioOpened(portfolio.getId(), portfolio.getCustomerId(), portfolio.getOwnerId(),
                portfolio.getName(), portfolio.getCurrency(), portfolio.getCreatedAt());
        publish(PORTFOLIO_OPENED_EVENT_TYPE, portfolio.getId().toString(), payload, portfolio.getCreatedAt());

        return portfolio;
    }

    @Transactional
    public Position recordPosition(UUID portfolioId, String fundCode, BigDecimal quantity) {
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PORTFOLIO-4041",
                        "Portfolio not found: " + portfolioId));
        // Writes are staff-only (controller-level @PreAuthorize), so no ownership
        // check here — unlike the read paths, there's no investor self-service flow yet.

        Instant now = Instant.now();
        Position position = positionRepository.save(new Position(UUID.randomUUID(), portfolio.getId(), fundCode,
                quantity, now));

        var payload = new PositionRecorded(position.getId(), position.getPortfolioId(), position.getFundCode(),
                position.getQuantity(), position.getCreatedAt());
        publish(POSITION_RECORDED_EVENT_TYPE, position.getId().toString(), payload, position.getCreatedAt());

        return position;
    }

    private void enforceOwnership(Portfolio portfolio) {
        if (isStaff()) {
            return;
        }
        String callerId = CurrentUser.subject().orElse(null);
        if (callerId == null || !callerId.equals(portfolio.getOwnerId().toString())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "PORTFOLIO-4030", "You do not have access to this portfolio");
        }
    }

    private boolean isStaff() {
        return STAFF_ROLES.stream().anyMatch(CurrentUser::hasRole);
    }

    /**
     * The full {@link EventEnvelope} is stored as the outbox payload, not
     * just the raw domain data, so consumers see the mandatory envelope
     * shape (guide §22) on the wire once relayed.
     */
    private <T> void publish(String eventType, String aggregateId, T payload, Instant occurredAt) {
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        var envelope = new EventEnvelope<>(UUID.randomUUID(), eventType, occurredAt, correlationId, PRODUCER, 1,
                payload);
        outboxEventStore.write("Portfolio", aggregateId, eventType, envelope, correlationId, PRODUCER);
    }
}
