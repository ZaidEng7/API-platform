package com.company.kyc.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * A single KYC review for a Party (guide §8.3: this service is the target
 * SoR for "KYC status"; {@code customerId} references Customer Service's
 * Party by id only — services never reach into each other's databases,
 * §8.1). Deliberately no decisioning logic (rules engine, sanctions
 * screening) here: those business rules aren't defined yet (Compliance
 * sign-off on §3.1 is still pending — see project memory). This service
 * owns the review's lifecycle and system-of-record status; a human
 * Compliance reviewer supplies the actual decision via the API.
 */
@Entity
@Table(name = "kyc_checks")
public class KycCheck {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private KycCheckStatus status;

    private String reason;

    private String decidedBy;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected KycCheck() {
    }

    public KycCheck(UUID id, UUID customerId, Instant createdAt) {
        this.id = id;
        this.customerId = customerId;
        this.status = KycCheckStatus.PENDING;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    /**
     * @throws IllegalStateException if this check has already been decided — a
     *                                KYC decision is made once per check; a
     *                                changed mind means requesting a new check.
     */
    public void decide(KycCheckStatus outcome, String reason, String decidedBy) {
        if (this.status != KycCheckStatus.PENDING) {
            throw new IllegalStateException("KYC check " + id + " was already decided (" + this.status + ")");
        }
        this.status = outcome;
        this.reason = reason;
        this.decidedBy = decidedBy;
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public KycCheckStatus getStatus() {
        return status;
    }

    public String getReason() {
        return reason;
    }

    public String getDecidedBy() {
        return decidedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
