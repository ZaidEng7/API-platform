package com.company.investment.infrastructure;

import com.company.investment.domain.Subscription;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Insert-first idempotency for {@code Subscription.idempotencyKey} (unique,
 * see {@code V1__init_investment_schema.sql}): attempts the insert and
 * reports whether this call won the race, without poisoning the caller's
 * own transaction on conflict.
 *
 * <p>Same rationale as Portfolio Service's {@code PositionInsertGuard}: a
 * unique-constraint violation aborts the whole transaction at the Postgres
 * level, so catching it in the caller's own transaction would leave that
 * transaction unusable for the fallback lookup {@link
 * com.company.investment.application.SubscriptionApplicationService#requestSubscription}
 * needs to do afterward. A separate bean because Spring's proxy-based
 * transaction AOP doesn't intercept same-class self-invocation.
 */
@Component
public class SubscriptionInsertGuard {

    private final SubscriptionJpaRepository subscriptionRepository;

    public SubscriptionInsertGuard(SubscriptionJpaRepository subscriptionRepository) {
        this.subscriptionRepository = subscriptionRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<Subscription> tryInsert(Subscription subscription) {
        try {
            return Optional.of(subscriptionRepository.saveAndFlush(subscription));
        } catch (DataIntegrityViolationException e) {
            return Optional.empty();
        }
    }
}
