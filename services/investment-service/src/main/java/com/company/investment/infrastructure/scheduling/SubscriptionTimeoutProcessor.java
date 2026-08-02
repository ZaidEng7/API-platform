package com.company.investment.infrastructure.scheduling;

import com.company.investment.domain.Subscription;
import com.company.investment.domain.SubscriptionStatus;
import com.company.investment.domain.event.SubscriptionTimedOut;
import com.company.investment.infrastructure.SubscriptionJpaRepository;
import com.company.platform.messaging.envelope.EventEnvelope;
import com.company.platform.messaging.outbox.OutboxEventStore;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * One subscription's timeout, in its own transaction — split out of
 * {@link SubscriptionTimeoutJob} so a single row losing a race against
 * {@code confirmPayment()}/{@code cancel()} (both of which can commit
 * between this job's batch query and this row's own processing) only
 * rolls back that one row's transaction, not the whole batch. A separate
 * bean because Spring's proxy-based transaction AOP doesn't intercept
 * same-class self-invocation.
 */
@Component
public class SubscriptionTimeoutProcessor {

    private static final String TIMED_OUT_EVENT_TYPE = "investment.subscription.timed-out";
    private static final String PRODUCER = "investment-service";

    private final SubscriptionJpaRepository subscriptionRepository;
    private final OutboxEventStore outboxEventStore;

    public SubscriptionTimeoutProcessor(SubscriptionJpaRepository subscriptionRepository,
                                         OutboxEventStore outboxEventStore) {
        this.subscriptionRepository = subscriptionRepository;
        this.outboxEventStore = outboxEventStore;
    }

    /**
     * Re-fetches {@code id} fresh (the caller's batch query may be stale by
     * the time this runs) and re-checks it's still eligible before timing
     * it out. Returns {@code false} — logged by the caller, not an error
     * here — if the row was concurrently confirmed/cancelled/already timed
     * out, or lost the optimistic-lock race to one of those.
     */
    @Transactional
    public boolean tryTimeOut(UUID id) {
        Subscription subscription = subscriptionRepository.findById(id).orElse(null);
        if (subscription == null || subscription.getStatus() != SubscriptionStatus.AWAITING_PAYMENT
                || subscription.getTimeoutAt() == null || subscription.getTimeoutAt().isAfter(Instant.now())) {
            return false;
        }

        try {
            subscription.timeout();
            subscriptionRepository.saveAndFlush(subscription);
        } catch (IllegalStateException | OptimisticLockingFailureException e) {
            return false;
        }

        var payload = new SubscriptionTimedOut(subscription.getId(), subscription.getCustomerId(),
                subscription.getUpdatedAt());
        var envelope = new EventEnvelope<>(UUID.randomUUID(), TIMED_OUT_EVENT_TYPE, subscription.getUpdatedAt(),
                null, PRODUCER, 1, payload);
        outboxEventStore.write("Subscription", subscription.getId().toString(), TIMED_OUT_EVENT_TYPE, envelope, null,
                PRODUCER);
        return true;
    }
}
