package com.company.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A single payment movement — guide §8.3 target SoR for "Payment status"
 * (interim SoR: "PSP + Back Office", no adapter built for either yet, same
 * situation Portfolio Service had with "Portfolio product"). {@code
 * paymentMethodToken} is an opaque PSP-issued reference, never a raw card
 * number — guide §3.1/§12.5: "card data never enters the platform; use
 * tokenization via PSP" (PCI-DSS v4.0 scope isolation). Enforced
 * structurally at the API boundary, not just by convention — see {@link
 * com.company.payment.api.dto.RequestTransferRequest}'s validation.
 *
 * <p>No real PSP integration exists (Phase 4 never built one) — settlement
 * is confirmed via a human/finance-ops action ({@link #settle}/{@link
 * #fail}), the same "human/future-service supplies the missing piece"
 * pattern KYC/AML/Document/Investment Service all established. {@code
 * reference} is a caller-supplied, service-agnostic string (e.g. a
 * subscription id) — Payment Service doesn't hold a hard dependency on
 * whatever business object a transfer is for.
 */
@Entity
@Table(name = "transfers")
public class Transfer {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String idempotencyKey;

    @Column(nullable = false)
    private UUID customerId;

    @Column(nullable = false)
    private UUID ownerId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false)
    private String paymentMethodToken;

    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransferStatus status;

    private String failureReason;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    private Instant settledAt;

    protected Transfer() {
    }

    public Transfer(UUID id, String idempotencyKey, UUID customerId, UUID ownerId, BigDecimal amount,
                     String currency, String paymentMethodToken, String reference, Instant createdAt) {
        this.id = id;
        this.idempotencyKey = idempotencyKey;
        this.customerId = customerId;
        this.ownerId = ownerId;
        this.amount = amount;
        this.currency = currency;
        this.paymentMethodToken = paymentMethodToken;
        this.reference = reference;
        this.status = TransferStatus.PENDING;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    /** @throws IllegalStateException if this transfer isn't still PENDING. */
    public void settle() {
        requirePending();
        this.status = TransferStatus.SETTLED;
        this.updatedAt = Instant.now();
        this.settledAt = this.updatedAt;
    }

    /** @throws IllegalStateException if this transfer isn't still PENDING. */
    public void fail(String reason) {
        requirePending();
        this.status = TransferStatus.FAILED;
        this.failureReason = reason;
        this.updatedAt = Instant.now();
    }

    private void requirePending() {
        if (this.status != TransferStatus.PENDING) {
            throw new IllegalStateException("Transfer " + id + " is no longer pending (" + this.status + ")");
        }
    }

    public UUID getId() {
        return id;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getPaymentMethodToken() {
        return paymentMethodToken;
    }

    public String getReference() {
        return reference;
    }

    public TransferStatus getStatus() {
        return status;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getSettledAt() {
        return settledAt;
    }
}
