package com.company.platform.messaging.outbox;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * A row in the {@code outbox_events} table — see this module's README for
 * the DDL each owning service must add to its own Flyway migrations
 * (guide §8.2: database-per-service, so this table lives in the caller's
 * schema, not a shared one).
 */
@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String aggregateType;

    @Column(nullable = false)
    private String aggregateId;

    @Column(nullable = false)
    private String eventType;

    // No @Lob: on PostgreSQL, @Lob + String maps to the `oid` large-object
    // type by default, not `text` — a plain String column matches the
    // documented TEXT DDL correctly.
    @Column(nullable = false)
    private String payload;

    @Column(nullable = false)
    private Instant occurredAt;

    private String correlationId;

    @Column(nullable = false)
    private String producer;

    @Column(nullable = false)
    private int schemaVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutboxEventStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant publishedAt;

    protected OutboxEvent() {
    }

    public OutboxEvent(UUID id, String aggregateType, String aggregateId, String eventType, String payload,
                        Instant occurredAt, String correlationId, String producer, int schemaVersion) {
        this.id = id;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.occurredAt = occurredAt;
        this.correlationId = correlationId;
        this.producer = producer;
        this.schemaVersion = schemaVersion;
        this.status = OutboxEventStatus.PENDING;
        this.attempts = 0;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public String getProducer() {
        return producer;
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public OutboxEventStatus getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    void markPublished() {
        this.status = OutboxEventStatus.PUBLISHED;
        this.publishedAt = Instant.now();
    }

    /** Stays PENDING (retried on the next poll) until {@code maxAttempts} is exceeded, then FAILED. */
    void recordFailure(int maxAttempts) {
        this.attempts++;
        if (this.attempts >= maxAttempts) {
            this.status = OutboxEventStatus.FAILED;
        }
    }
}
