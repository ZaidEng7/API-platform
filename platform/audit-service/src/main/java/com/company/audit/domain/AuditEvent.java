package com.company.audit.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * An immutable audit record (guide §13). Append-only by design: no setters
 * beyond construction, and nothing in this codebase updates or deletes a
 * row once written.
 */
@Entity
@Table(name = "audit_events")
public class AuditEvent {

    @Id
    private UUID id;

    /** The originating domain event's id — dedupe key (guide §22). */
    @Column(nullable = false, unique = true)
    private UUID sourceEventId;

    @Column(nullable = false)
    private String eventType;

    private String actor;

    @Column(nullable = false)
    private Instant occurredAt;

    @Column(nullable = false)
    private Instant recordedAt;

    private String correlationId;

    @Lob
    @Column(nullable = false)
    private String rawPayload;

    protected AuditEvent() {
    }

    public AuditEvent(UUID id, UUID sourceEventId, String eventType, String actor, Instant occurredAt,
                       String correlationId, String rawPayload) {
        this.id = id;
        this.sourceEventId = sourceEventId;
        this.eventType = eventType;
        this.actor = actor;
        this.occurredAt = occurredAt;
        this.recordedAt = Instant.now();
        this.correlationId = correlationId;
        this.rawPayload = rawPayload;
    }

    public UUID getId() {
        return id;
    }

    public UUID getSourceEventId() {
        return sourceEventId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getActor() {
        return actor;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public String getRawPayload() {
        return rawPayload;
    }
}
