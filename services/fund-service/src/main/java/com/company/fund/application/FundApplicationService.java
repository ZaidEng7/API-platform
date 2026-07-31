package com.company.fund.application;

import com.company.fund.domain.Fund;
import com.company.fund.domain.NavSnapshot;
import com.company.fund.domain.event.FundNavUpdated;
import com.company.fund.domain.event.FundRegistered;
import com.company.fund.infrastructure.FundJpaRepository;
import com.company.fund.infrastructure.NavSnapshotJpaRepository;
import com.company.fund.infrastructure.client.FundNavClient;
import com.company.platform.messaging.envelope.EventEnvelope;
import com.company.platform.messaging.outbox.OutboxEventStore;
import com.company.platform.web.correlation.CorrelationIdFilter;
import com.company.platform.web.exception.ApiException;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class FundApplicationService {

    private static final String FUND_REGISTERED_EVENT_TYPE = "fund.definition.registered";
    private static final String NAV_UPDATED_EVENT_TYPE = "fund.nav.updated";
    private static final String PRODUCER = "fund-service";

    private final FundJpaRepository fundRepository;
    private final NavSnapshotJpaRepository navSnapshotRepository;
    private final FundNavClient fundNavClient;
    private final OutboxEventStore outboxEventStore;

    public FundApplicationService(FundJpaRepository fundRepository, NavSnapshotJpaRepository navSnapshotRepository,
                                   FundNavClient fundNavClient, OutboxEventStore outboxEventStore) {
        this.fundRepository = fundRepository;
        this.navSnapshotRepository = navSnapshotRepository;
        this.fundNavClient = fundNavClient;
        this.outboxEventStore = outboxEventStore;
    }

    @Transactional(readOnly = true)
    public Fund getByCode(String fundCode) {
        return fundRepository.findByFundCode(fundCode)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "FUND-4041", "Fund not found: " + fundCode));
    }

    @Transactional(readOnly = true)
    public Page<Fund> listFunds(Pageable pageable) {
        return fundRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public NavSnapshot getLatestNav(String fundCode) {
        getByCode(fundCode); // 404s if the fund itself doesn't exist
        return navSnapshotRepository.findFirstByFundCodeOrderByFetchedAtDesc(fundCode)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "FUND-4042",
                        "No NAV data yet for fund: " + fundCode));
    }

    @Transactional(readOnly = true)
    public Page<NavSnapshot> getNavHistory(String fundCode, Pageable pageable) {
        getByCode(fundCode); // 404s if the fund itself doesn't exist
        return navSnapshotRepository.findByFundCode(fundCode, pageable);
    }

    /**
     * Writes the outbox row in the SAME transaction as the fund insert
     * (guide §8.4: no dual-write without outbox).
     */
    @Transactional
    public Fund registerFund(String fundCode, String name, String currency) {
        if (fundRepository.existsByFundCode(fundCode)) {
            throw new ApiException(HttpStatus.CONFLICT, "FUND-4091", "Fund already registered: " + fundCode);
        }

        Instant now = Instant.now();
        Fund fund = fundRepository.save(new Fund(UUID.randomUUID(), fundCode, name, currency, now));

        var payload = new FundRegistered(fund.getId(), fund.getFundCode(), fund.getName(), fund.getCurrency(),
                fund.getCreatedAt());
        publish(FUND_REGISTERED_EVENT_TYPE, fund.getId().toString(), payload, fund.getCreatedAt());

        return fund;
    }

    /**
     * Calls fund-mgmt-adapter for this fund's current NAV and stores it as
     * a new snapshot — this service never talks to the legacy Fund
     * Management product directly (guide §8.1), only through that adapter.
     */
    @Transactional
    public NavSnapshot refreshNav(String fundCode) {
        getByCode(fundCode); // 404s if the fund itself doesn't exist

        var navFromAdapter = fundNavClient.getNav(fundCode);
        Instant fetchedAt = Instant.now();
        NavSnapshot snapshot = navSnapshotRepository.save(new NavSnapshot(UUID.randomUUID(), fundCode,
                navFromAdapter.navPerShare(), navFromAdapter.asOfDate(), fetchedAt));

        var payload = new FundNavUpdated(snapshot.getFundCode(), snapshot.getNavPerShare(), snapshot.getAsOfDate(),
                snapshot.getFetchedAt());
        publish(NAV_UPDATED_EVENT_TYPE, fundCode, payload, fetchedAt);

        return snapshot;
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
        outboxEventStore.write("Fund", aggregateId, eventType, envelope, correlationId, PRODUCER);
    }
}
