package com.company.platform.messaging.envelope;

import java.time.Instant;
import java.util.UUID;

/**
 * Mandatory event envelope (guide §22). {@code eventType} follows
 * {@code <domain>.<entity>.<event-past-tense>} (e.g.
 * {@code customer.kyc.approved}) and doubles as the RabbitMQ routing key.
 */
public record EventEnvelope<T>(
        UUID eventId,
        String eventType,
        Instant occurredAt,
        String correlationId,
        String producer,
        int schemaVersion,
        T payload) {
}
