package com.company.investment.application;

import com.company.investment.domain.Subscription;
import com.company.investment.domain.SubscriptionStatus;
import com.company.investment.domain.event.SubscriptionCancelled;
import com.company.investment.domain.event.SubscriptionConfirmed;
import com.company.investment.domain.event.SubscriptionFailed;
import com.company.investment.domain.event.SubscriptionReserved;
import com.company.investment.infrastructure.SubscriptionInsertGuard;
import com.company.investment.infrastructure.SubscriptionJpaRepository;
import com.company.investment.infrastructure.client.AmlScreeningClient;
import com.company.investment.infrastructure.client.CustomerServiceClient;
import com.company.investment.infrastructure.client.KycCheckClient;
import com.company.investment.infrastructure.client.PortfolioPositionClient;
import com.company.platform.messaging.envelope.EventEnvelope;
import com.company.platform.messaging.outbox.OutboxEventStore;
import com.company.platform.security.CurrentUser;
import com.company.platform.web.correlation.CorrelationIdFilter;
import com.company.platform.web.exception.ApiException;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Drives the fund subscription saga guide §8.4 names explicitly ("validate
 * customer → KYC/AML check → reserve units → collect payment → confirm →
 * notify") without distributed 2PC. "Collect payment" has no real callee
 * yet — Payment Service is Phase 5 item 8, built after this one in the
 * guide's own dependency order — so it's modeled as a human/finance-ops
 * confirmation step ({@link #confirmPayment}) instead of inventing a fake
 * PSP integration, the same "human/future-service supplies the missing
 * piece" pattern KYC/AML/Document Service established.
 */
@Service
public class SubscriptionApplicationService {

    private static final List<String> STAFF_ROLES =
            List.of("OPERATIONS", "PORTFOLIO_MANAGER", "AUDITOR", "COMPLIANCE");

    private static final String RESERVED_EVENT_TYPE = "investment.subscription.reserved";
    private static final String FAILED_EVENT_TYPE = "investment.subscription.failed";
    private static final String CONFIRMED_EVENT_TYPE = "investment.subscription.confirmed";
    private static final String CANCELLED_EVENT_TYPE = "investment.subscription.cancelled";
    private static final String PRODUCER = "investment-service";

    private final SubscriptionJpaRepository subscriptionRepository;
    private final SubscriptionInsertGuard subscriptionInsertGuard;
    private final CustomerServiceClient customerServiceClient;
    private final KycCheckClient kycCheckClient;
    private final AmlScreeningClient amlScreeningClient;
    private final PortfolioPositionClient portfolioPositionClient;
    private final OutboxEventStore outboxEventStore;
    private final Duration timeout;

    public SubscriptionApplicationService(SubscriptionJpaRepository subscriptionRepository,
                                           SubscriptionInsertGuard subscriptionInsertGuard,
                                           CustomerServiceClient customerServiceClient, KycCheckClient kycCheckClient,
                                           AmlScreeningClient amlScreeningClient,
                                           PortfolioPositionClient portfolioPositionClient,
                                           OutboxEventStore outboxEventStore,
                                           @Value("${investment.subscription.timeout}") Duration timeout) {
        this.subscriptionRepository = subscriptionRepository;
        this.subscriptionInsertGuard = subscriptionInsertGuard;
        this.customerServiceClient = customerServiceClient;
        this.kycCheckClient = kycCheckClient;
        this.amlScreeningClient = amlScreeningClient;
        this.portfolioPositionClient = portfolioPositionClient;
        this.outboxEventStore = outboxEventStore;
        this.timeout = timeout;
    }

    @Transactional(readOnly = true)
    public Subscription getById(UUID id) {
        Subscription subscription = subscriptionRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "INV-4041", "Subscription not found: " + id));
        enforceOwnership(subscription);
        return subscription;
    }

    /** @throws ApiException 403 if an investor-only caller queries another investor's {@code ownerId}. */
    @Transactional(readOnly = true)
    public Page<Subscription> listByOwner(UUID ownerId, Pageable pageable) {
        if (!isStaff() && !ownerId.toString().equals(CurrentUser.subject().orElse(null))) {
            throw new ApiException(HttpStatus.FORBIDDEN, "INV-4030", "You do not have access to that owner's subscriptions");
        }
        return subscriptionRepository.findByOwnerId(ownerId, pageable);
    }

    /**
     * Idempotent on {@code idempotencyKey} (guide §12.3: financial-effect
     * POSTs require a client-generated {@code Idempotency-Key}) — a replayed
     * request with the same key returns the original subscription rather
     * than creating a duplicate, including under genuine concurrency: two
     * requests carrying the same key that both pass the initial check
     * before either commits (a real client-retry-after-slow-response
     * scenario) are resolved by {@link SubscriptionInsertGuard}, not by a
     * naive check-then-act that would let the second one crash on the
     * unique-constraint violation instead of returning the first one's
     * result. Runs "validate customer → KYC/AML check → reserve"
     * synchronously: all three downstream services are our own fast REST
     * APIs, not a legacy batch process, so there's no need to persist
     * intermediate saga state between these particular steps — only the
     * genuinely long-running "await payment" step needs that (see
     * {@link #confirmPayment}, {@link #cancel}, and the scheduled timeout
     * job).
     */
    @Transactional
    public Subscription requestSubscription(String idempotencyKey, UUID customerId, UUID ownerId, UUID portfolioId,
                                             String fundCode, BigDecimal quantity) {
        var existing = subscriptionRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return existing.get();
        }

        customerServiceClient.requireExists(customerId);

        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        boolean kycApproved = kycCheckClient.isApproved(customerId);
        boolean amlClear = amlScreeningClient.isClear(customerId);

        Subscription candidate;
        String eventType;
        Object eventPayload;
        if (!kycApproved || !amlClear) {
            String reason = !kycApproved && !amlClear ? "KYC not approved and AML screening not clear"
                    : !kycApproved ? "KYC not approved" : "AML screening not clear";
            candidate = Subscription.failed(id, idempotencyKey, customerId, ownerId, portfolioId, fundCode, quantity,
                    reason, now);
            eventType = FAILED_EVENT_TYPE;
            eventPayload = new SubscriptionFailed(id, customerId, reason, now);
        } else {
            candidate = Subscription.reserved(id, idempotencyKey, customerId, ownerId, portfolioId, fundCode,
                    quantity, now, now.plus(timeout));
            eventType = RESERVED_EVENT_TYPE;
            eventPayload = new SubscriptionReserved(id, customerId, portfolioId, fundCode, quantity, now);
        }

        var inserted = subscriptionInsertGuard.tryInsert(candidate);
        if (inserted.isPresent()) {
            publish(eventType, id.toString(), eventPayload, now);
            return inserted.get();
        }

        // Lost a genuine concurrent race: another request with the same
        // idempotencyKey committed between our check above and our insert
        // attempt. That request already published its own event — return
        // its result rather than publishing a duplicate.
        return subscriptionRepository.findByIdempotencyKey(idempotencyKey)
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "INV-4092",
                        "Subscription insert conflicted on idempotencyKey " + idempotencyKey
                                + " but no existing subscription was found"));
    }

    /**
     * The subscription stays AWAITING_PAYMENT until Portfolio Service's
     * call succeeds — checked before calling out, not after, so a failed
     * or already-confirmed subscription never triggers a duplicate
     * position (guide §12.3: saga steps must be idempotent).
     *
     * <p>The status transition is claimed (mutated + flushed, so a stale
     * {@link com.company.investment.domain.Subscription}'s optimistic-lock
     * {@code version} conflict surfaces here) <em>before</em> calling
     * Portfolio Service, not after. {@link SubscriptionTimeoutJob} can race
     * this same row from an independent transaction; without claiming
     * first, a lost race would only be discovered after the position was
     * already recorded — this way, a lost race means the external call is
     * never made at all, and this method's own rollback-on-failure
     * behavior (nothing commits until the method returns normally) means a
     * confirmPayment failure after the flush still safely reverts to
     * AWAITING_PAYMENT.
     *
     * @throws ApiException 409 if this subscription isn't AWAITING_PAYMENT,
     *                       or was concurrently modified (e.g. timed out)
     *                       between being read and being claimed here.
     */
    @Transactional
    public Subscription confirmPayment(UUID id) {
        Subscription subscription = subscriptionRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "INV-4041", "Subscription not found: " + id));
        if (subscription.getStatus() != SubscriptionStatus.AWAITING_PAYMENT) {
            throw new ApiException(HttpStatus.CONFLICT, "INV-4091",
                    "Subscription " + id + " is not awaiting payment (" + subscription.getStatus() + ")");
        }

        subscription.confirm();
        try {
            subscriptionRepository.saveAndFlush(subscription);
        } catch (OptimisticLockingFailureException e) {
            throw new ApiException(HttpStatus.CONFLICT, "INV-4092",
                    "Subscription " + id + " was concurrently modified (e.g. timed out) — refresh and retry");
        }

        portfolioPositionClient.recordPosition(subscription.getPortfolioId(), subscription.getFundCode(),
                subscription.getQuantity(), subscription.getId());

        var payload = new SubscriptionConfirmed(subscription.getId(), subscription.getCustomerId(),
                subscription.getPortfolioId(), subscription.getFundCode(), subscription.getQuantity(),
                subscription.getConfirmedAt());
        publish(CONFIRMED_EVENT_TYPE, id.toString(), payload, subscription.getConfirmedAt());

        return subscription;
    }

    /**
     * The compensating action for a subscription that never gets paid —
     * see {@link Subscription#cancel()}'s Javadoc.
     *
     * @throws ApiException 409 if this subscription isn't AWAITING_PAYMENT,
     *                       or was concurrently modified (e.g. already
     *                       confirmed or timed out) between being read and
     *                       being claimed here.
     */
    @Transactional
    public Subscription cancel(UUID id) {
        Subscription subscription = subscriptionRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "INV-4041", "Subscription not found: " + id));
        try {
            subscription.cancel();
            subscriptionRepository.saveAndFlush(subscription);
        } catch (IllegalStateException e) {
            throw new ApiException(HttpStatus.CONFLICT, "INV-4091", e.getMessage());
        } catch (OptimisticLockingFailureException e) {
            throw new ApiException(HttpStatus.CONFLICT, "INV-4092",
                    "Subscription " + id + " was concurrently modified (e.g. already confirmed or timed out) — refresh and retry");
        }

        publish(CANCELLED_EVENT_TYPE, id.toString(),
                new SubscriptionCancelled(subscription.getId(), subscription.getCustomerId(), subscription.getUpdatedAt()),
                subscription.getUpdatedAt());

        return subscription;
    }

    private void enforceOwnership(Subscription subscription) {
        if (isStaff()) {
            return;
        }
        String callerId = CurrentUser.subject().orElse(null);
        if (callerId == null || !callerId.equals(subscription.getOwnerId().toString())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "INV-4030", "You do not have access to this subscription");
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
        outboxEventStore.write("Subscription", aggregateId, eventType, envelope, correlationId, PRODUCER);
    }
}
