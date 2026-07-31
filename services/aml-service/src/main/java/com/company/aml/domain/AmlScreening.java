package com.company.aml.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * A single watchlist/sanctions screening for a Party (guide §10.3: AML
 * screening is long-running/async, never held open on an HTTP connection
 * waiting on a legacy batch process — {@code customerId} references
 * Customer Service's Party by id only, §8.1). No real watchlist vendor is
 * integrated yet (that's an anti-corruption-layer adapter, guide §9, not
 * built for AML — Phase 1/4 legacy integration is still deferred). This
 * service owns the screening's lifecycle and status; a human Compliance
 * reviewer supplies the CLEAR/HIT determination via the API, and Operations
 * can mark a screening technically FAILED (e.g. an eventual vendor adapter
 * being unavailable) — distinct from a compliance HIT.
 */
@Entity
@Table(name = "aml_screenings")
public class AmlScreening {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ScreeningStatus status;

    @Enumerated(EnumType.STRING)
    private ScreeningOutcome outcome;

    private String notes;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    private Instant completedAt;

    protected AmlScreening() {
    }

    public AmlScreening(UUID id, UUID customerId, Instant createdAt) {
        this.id = id;
        this.customerId = customerId;
        this.status = ScreeningStatus.IN_PROGRESS;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    /** @throws IllegalStateException if this screening isn't still IN_PROGRESS. */
    public void complete(ScreeningOutcome outcome, String notes) {
        requireInProgress();
        this.status = ScreeningStatus.COMPLETED;
        this.outcome = outcome;
        this.notes = notes;
        this.updatedAt = Instant.now();
        this.completedAt = this.updatedAt;
    }

    /** @throws IllegalStateException if this screening isn't still IN_PROGRESS. */
    public void fail(String reason) {
        requireInProgress();
        this.status = ScreeningStatus.FAILED;
        this.notes = reason;
        this.updatedAt = Instant.now();
    }

    private void requireInProgress() {
        if (this.status != ScreeningStatus.IN_PROGRESS) {
            throw new IllegalStateException("AML screening " + id + " is no longer in progress (" + this.status + ")");
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public ScreeningStatus getStatus() {
        return status;
    }

    public ScreeningOutcome getOutcome() {
        return outcome;
    }

    public String getNotes() {
        return notes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
