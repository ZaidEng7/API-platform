package com.company.investment.infrastructure.scheduling;

import com.company.investment.domain.Subscription;
import com.company.investment.domain.SubscriptionStatus;
import com.company.investment.infrastructure.SubscriptionJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Guide §8.4: "every saga has a timeout and a dead-letter path with
 * operational alerting — a stuck subscription must page someone, not
 * silently rot." Finds AWAITING_PAYMENT subscriptions past their
 * {@code timeoutAt} and hands each one to {@link SubscriptionTimeoutProcessor}
 * individually — deliberately not as one batch transaction, so a single
 * row losing an optimistic-lock race against {@code confirmPayment()}/
 * {@code cancel()} (both can commit between this query and that row's own
 * processing) only skips that one row instead of rolling back the whole
 * batch. Reuses the same {@code @Scheduled} mechanism common-messaging's
 * own {@code OutboxRelayPublisher} already established in this codebase,
 * not new infrastructure.
 */
@Component
public class SubscriptionTimeoutJob {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionTimeoutJob.class);

    private final SubscriptionJpaRepository subscriptionRepository;
    private final SubscriptionTimeoutProcessor timeoutProcessor;

    public SubscriptionTimeoutJob(SubscriptionJpaRepository subscriptionRepository,
                                   SubscriptionTimeoutProcessor timeoutProcessor) {
        this.subscriptionRepository = subscriptionRepository;
        this.timeoutProcessor = timeoutProcessor;
    }

    @Scheduled(fixedDelayString = "${investment.subscription.timeout-check-interval-ms:60000}")
    public void timeOutStaleSubscriptions() {
        List<UUID> staleIds = subscriptionRepository.findByStatusAndTimeoutAtBefore(
                SubscriptionStatus.AWAITING_PAYMENT, Instant.now()).stream().map(Subscription::getId).toList();

        for (UUID id : staleIds) {
            if (timeoutProcessor.tryTimeOut(id)) {
                log.warn("Subscription {} timed out awaiting payment", id);
            } else {
                log.debug("Subscription {} no longer eligible for timeout (concurrently modified or already handled)",
                        id);
            }
        }
    }
}
