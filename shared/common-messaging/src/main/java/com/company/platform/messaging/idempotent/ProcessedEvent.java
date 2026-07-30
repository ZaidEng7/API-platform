package com.company.platform.messaging.idempotent;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * A row in the {@code processed_events} table — see this module's README
 * for the DDL each consuming service must add to its own Flyway
 * migrations. The unique constraint on (event_id, consumer_name) is the
 * actual dedupe enforcement (guide §22: consumers deduplicate on event ID).
 */
@Entity
@Table(name = "processed_events",
        uniqueConstraints = @UniqueConstraint(columnNames = {"event_id", "consumer_name"}))
public class ProcessedEvent {

    @Id
    private UUID id;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "consumer_name", nullable = false)
    private String consumerName;

    @Column(nullable = false)
    private Instant processedAt;

    protected ProcessedEvent() {
    }

    public ProcessedEvent(UUID eventId, String consumerName) {
        this.id = UUID.randomUUID();
        this.eventId = eventId;
        this.consumerName = consumerName;
        this.processedAt = Instant.now();
    }
}
