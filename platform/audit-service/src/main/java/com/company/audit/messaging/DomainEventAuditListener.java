package com.company.audit.messaging;

import com.company.audit.application.AuditEventRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

/**
 * Consumes every domain event and writes an immutable audit record.
 *
 * <p>Known gap: {@link com.company.platform.messaging.outbox.OutboxRelayPublisher}
 * publishes only the raw payload, not the full
 * {@link com.company.platform.messaging.envelope.EventEnvelope} — so the
 * envelope's real {@code occurredAt} isn't on the wire yet, and this falls
 * back to consumption time. Producers that need the true event time in the
 * audit trail should call {@code OutboxEventStore.write()} with the
 * envelope itself as the payload (see common-messaging's README).
 */
@Component
public class DomainEventAuditListener {

    private static final Logger log = LoggerFactory.getLogger(DomainEventAuditListener.class);

    private final AuditEventRecorder auditEventRecorder;

    public DomainEventAuditListener(AuditEventRecorder auditEventRecorder) {
        this.auditEventRecorder = auditEventRecorder;
    }

    @RabbitListener(queues = AuditMessagingConfig.QUEUE_NAME)
    public void onDomainEvent(Message message) {
        MessageProperties props = message.getMessageProperties();
        UUID sourceEventId = parseMessageId(props.getMessageId());
        String eventType = props.getReceivedRoutingKey();
        String correlationId = props.getCorrelationId();
        String payload = new String(message.getBody(), StandardCharsets.UTF_8);

        boolean recorded = auditEventRecorder.record(sourceEventId, eventType, null, Instant.now(),
                correlationId, payload);
        if (!recorded) {
            log.debug("Duplicate domain event {} ({}) — already audited", sourceEventId, eventType);
        }
    }

    private UUID parseMessageId(String messageId) {
        if (messageId == null) {
            return UUID.randomUUID();
        }
        try {
            return UUID.fromString(messageId);
        } catch (IllegalArgumentException e) {
            return UUID.randomUUID();
        }
    }
}
