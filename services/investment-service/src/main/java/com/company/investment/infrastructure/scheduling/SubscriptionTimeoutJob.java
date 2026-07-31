package com.company.investment.infrastructure.scheduling;

import com.company.investment.domain.Subscription;
import com.company.investment.domain.SubscriptionStatus;
import com.company.investment.domain.event.SubscriptionTimedOut;
import com.company.investment.infrastructure.SubscriptionJpaRepository;
import com.company.platform.messaging.envelope.EventEnvelope;
import com.company.platform.messaging.outbox.OutboxEventStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Guide §8.4: "every saga has a timeout and a dead-letter path with
 * operational alerting — a stuck subscription must page someone, not
 * silently rot." Finds AWAITING_PAYMENT subscriptions past their
 * {@code timeoutAt} and moves them to TIMED_OUT, publishing an event a
 * real alerting pipeline (not built here — see §21/observability stack)
 * would page on. Reuses the same {@code @Scheduled} mechanism
 * common-messaging's own {@code OutboxRelayPublisher} already established
 * in this codebase, not new infrastructure.
 */
@Component
public class SubscriptionTimeoutJob {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionTimeoutJob.class);
    private static final String TIMED_OUT_EVENT_TYPE = "investment.subscription.timed-out";
    private static final String PRODUCER = "investment-service";

    private final SubscriptionJpaRepository subscriptionRepository;
    private final OutboxEventStore outboxEventStore;

    public SubscriptionTimeoutJob(SubscriptionJpaRepository subscriptionRepository, OutboxEventStore outboxEventStore) {
        this.subscriptionRepository = subscriptionRepository;
        this.outboxEventStore = outboxEventStore;
    }

    @Scheduled(fixedDelayString = "${investment.subscription.timeout-check-interval-ms:60000}")
    @Transactional
    public void timeOutStaleSubscriptions() {
        List<Subscription> stale = subscriptionRepository.findByStatusAndTimeoutAtBefore(
                SubscriptionStatus.AWAITING_PAYMENT, Instant.now());

        for (Subscription subscription : stale) {
            subscription.timeout();
            log.warn("Subscription {} timed out awaiting payment (customer {})", subscription.getId(),
                    subscription.getCustomerId());

            var payload = new SubscriptionTimedOut(subscription.getId(), subscription.getCustomerId(),
                    subscription.getUpdatedAt());
            var envelope = new EventEnvelope<>(UUID.randomUUID(), TIMED_OUT_EVENT_TYPE, subscription.getUpdatedAt(),
                    null, PRODUCER, 1, payload);
            outboxEventStore.write("Subscription", subscription.getId().toString(), TIMED_OUT_EVENT_TYPE, envelope,
                    null, PRODUCER);
        }
    }
}
