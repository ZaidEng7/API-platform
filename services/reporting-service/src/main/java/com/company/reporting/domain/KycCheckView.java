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
 * Read copy of KYC Service's own "KYC check" (guide §8.3 SoR matrix). Created
 * on {@code customer.kyc.requested}, updated to a terminal state on
 * {@code .approved}/{@code .rejected}.
 */
@Entity
@Table(name = "kyc_check_view")
public class KycCheckView {

    @Id
    private UUID checkId;

    @Column(nullable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private KycCheckReportStatus status;

    private String reason;
    private String decidedBy;

    @Column(nullable = false)
    private Instant requestedAt;

    private Instant decidedAt;

    protected KycCheckView() {
    }

    public KycCheckView(UUID checkId, UUID customerId, Instant requestedAt) {
        this.checkId = checkId;
        this.customerId = customerId;
        this.status = KycCheckReportStatus.PENDING;
        this.requestedAt = requestedAt;
    }

    public void decide(KycCheckReportStatus status, String reason, String decidedBy, Instant decidedAt) {
        this.status = status;
        this.reason = reason;
        this.decidedBy = decidedBy;
        this.decidedAt = decidedAt;
    }

    public UUID getCheckId() {
        return checkId;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public KycCheckReportStatus getStatus() {
        return status;
    }

    public String getReason() {
        return reason;
    }

    public String getDecidedBy() {
        return decidedBy;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }
}
