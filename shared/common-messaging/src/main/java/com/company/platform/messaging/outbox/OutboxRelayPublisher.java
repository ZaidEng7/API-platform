package com.company.platform.messaging.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Polls PENDING outbox rows and publishes them to the domain-events topic
 * exchange, using {@code eventType} as the routing key (guide §22 naming:
 * {@code <domain>.<entity>.<event-past-tense>}). A row stays PENDING (and is
 * retried) until {@code maxAttempts} is exceeded, then flips to FAILED for
 * manual/alerted intervention — actual dead-lettering of already-delivered-
 * but-unprocessable messages is a queue-level concern (guide §22: every
 * queue has a DLQ), not this class's job.
 */
public class OutboxRelayPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelayPublisher.class);

    private final OutboxEventStore outboxEventStore;
    private final RabbitTemplate rabbitTemplate;
    private final String exchange;
    private final int batchSize;
    private final int maxAttempts;

    public OutboxRelayPublisher(OutboxEventStore outboxEventStore, RabbitTemplate rabbitTemplate,
                                 String exchange, int batchSize, int maxAttempts) {
        this.outboxEventStore = outboxEventStore;
        this.rabbitTemplate = rabbitTemplate;
        this.exchange = exchange;
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
    }

    @Scheduled(fixedDelayString = "${platform.messaging.outbox-relay.interval-ms:2000}")
    public void relay() {
        for (OutboxEvent event : outboxEventStore.findPendingBatch(batchSize)) {
            try {
                rabbitTemplate.convertAndSend(exchange, event.getEventType(), event.getPayload(), message -> {
                    message.getMessageProperties().setMessageId(event.getId().toString());
                    if (event.getCorrelationId() != null) {
                        message.getMessageProperties().setCorrelationId(event.getCorrelationId());
                    }
                    return message;
                });
                outboxEventStore.markPublished(event.getId());
            } catch (Exception e) {
                log.error("Failed to publish outbox event {} ({}), attempt {}/{}",
                        event.getId(), event.getEventType(), event.getAttempts() + 1, maxAttempts, e);
                outboxEventStore.recordFailure(event.getId(), maxAttempts);
            }
        }
    }
}
