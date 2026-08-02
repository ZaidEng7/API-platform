package com.company.reporting.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Read copy of AML Service's own "AML screening" (guide §8.3 SoR matrix).
 * Created on {@code customer.aml.requested}, updated to a terminal state on
 * {@code .cleared}/{@code .flagged} (completed) or {@code .failed} (a
 * technical/system failure, not a compliance HIT).
 */
@Entity
@Table(name = "aml_screening_view")
public class AmlScreeningView {

    @Id
    private UUID screeningId;

    @Column(nullable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AmlScreeningReportStatus status;

    @Enumerated(EnumType.STRING)
    private AmlScreeningReportOutcome outcome;

    private String notes;
    private String failureReason;

    @Column(nullable = false)
    private Instant requestedAt;

    private Instant completedAt;
    private Instant failedAt;

    protected AmlScreeningView() {
    }

    public AmlScreeningView(UUID screeningId, UUID customerId, Instant requestedAt) {
        this.screeningId = screeningId;
        this.customerId = customerId;
        this.status = AmlScreeningReportStatus.IN_PROGRESS;
        this.requestedAt = requestedAt;
    }

    public void complete(AmlScreeningReportOutcome outcome, String notes, Instant completedAt) {
        this.status = AmlScreeningReportStatus.COMPLETED;
        this.outcome = outcome;
        this.notes = notes;
        this.completedAt = completedAt;
    }

    public void fail(String reason, Instant failedAt) {
        this.status = AmlScreeningReportStatus.FAILED;
        this.failureReason = reason;
        this.failedAt = failedAt;
    }

    public UUID getScreeningId() {
        return screeningId;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public AmlScreeningReportStatus getStatus() {
        return status;
    }

    public AmlScreeningReportOutcome getOutcome() {
        return outcome;
    }

    public String getNotes() {
        return notes;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Instant getFailedAt() {
        return failedAt;
    }
}
