package com.company.investment.infrastructure;

import com.company.investment.domain.Subscription;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Insert-first idempotency for {@code Subscription.idempotencyKey} (unique,
 * see {@code V1__init_investment_schema.sql}): attempts the insert in its
 * own transaction, without poisoning the caller's own transaction on
 * conflict.
 *
 * <p>Same rationale as Portfolio Service's {@code PositionInsertGuard}: a
 * unique-constraint violation aborts the whole transaction at the Postgres
 * level, so catching it in the caller's own transaction would leave that
 * transaction unusable for the fallback lookup {@link
 * com.company.investment.application.SubscriptionApplicationService#requestSubscription}
 * needs to do afterward.
 *
 * <p><strong>Deliberately does not catch the conflict itself</strong> —
 * see {@code PositionInsertGuard}'s Javadoc for why: catching inside a
 * {@code @Transactional(REQUIRES_NEW)} method and returning normally makes
 * Spring try to commit an already-Postgres-aborted transaction, which
 * throws its own uncaught exception. Letting the conflict propagate lets
 * Spring roll back this transaction correctly before re-throwing to the
 * caller, whose own (merely suspended, never touched) transaction is still
 * healthy for the fallback lookup.
 *
 * <p>A separate bean because Spring's proxy-based transaction AOP doesn't
 * intercept same-class self-invocation.
 */
@Component
public class SubscriptionInsertGuard {

    private final SubscriptionJpaRepository subscriptionRepository;

    public SubscriptionInsertGuard(SubscriptionJpaRepository subscriptionRepository) {
        this.subscriptionRepository = subscriptionRepository;
    }

    /** @throws org.springframework.dao.DataIntegrityViolationException if {@code subscription}'s idempotencyKey is already taken. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Subscription insert(Subscription subscription) {
        return subscriptionRepository.saveAndFlush(subscription);
    }
}
