package com.company.investment.infrastructure.scheduling;

import com.company.investment.domain.Subscription;
import com.company.investment.domain.SubscriptionStatus;
import com.company.investment.domain.event.SubscriptionTimedOut;
import com.company.investment.infrastructure.SubscriptionJpaRepository;
import com.company.platform.messaging.envelope.EventEnvelope;
import com.company.platform.messaging.outbox.OutboxEventStore;
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
     * it out. Returns {@code false} if the row was concurrently
     * confirmed/cancelled/already timed out before this method's own read.
     *
     * @throws org.springframework.dao.OptimisticLockingFailureException if
     *         the row was concurrently modified between this method's read
     *         and its flush (i.e. it lost the race, rather than simply
     *         arriving late to an already-changed row) — deliberately not
     *         caught here. Catching it inside this {@code @Transactional}
     *         method and returning normally would make Spring try to commit
     *         an already-Postgres-aborted transaction, which throws its own
     *         (different, uncaught) exception — see {@code PositionInsertGuard}'s
     *         Javadoc for the same reasoning. The caller ({@link SubscriptionTimeoutJob})
     *         catches this instead, outside this transaction's boundary.
     */
    @Transactional
    public boolean timeOut(UUID id) {
        Subscription subscription = subscriptionRepository.findById(id).orElse(null);
        if (subscription == null || subscription.getStatus() != SubscriptionStatus.AWAITING_PAYMENT
                || subscription.getTimeoutAt() == null || subscription.getTimeoutAt().isAfter(Instant.now())) {
            return false;
        }

        try {
            subscription.timeout();
        } catch (IllegalStateException e) {
            // No DB write attempted yet in this branch, so no transaction to poison.
            return false;
        }
        subscriptionRepository.saveAndFlush(subscription);

        var payload = new SubscriptionTimedOut(subscription.getId(), subscription.getCustomerId(),
                subscription.getUpdatedAt());
        var envelope = new EventEnvelope<>(UUID.randomUUID(), TIMED_OUT_EVENT_TYPE, subscription.getUpdatedAt(),
                null, PRODUCER, 1, payload);
        outboxEventStore.write("Subscription", subscription.getId().toString(), TIMED_OUT_EVENT_TYPE, envelope, null,
                PRODUCER);
        return true;
    }
}
