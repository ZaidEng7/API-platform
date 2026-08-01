package com.company.reporting.domain;

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
 * Read copy of Payment Service's "Payment status" (guide §8.3 SoR matrix:
 * "Read copies allowed in: Investment, Reporting"). Created on
 * {@code payment.transfer.requested}, updated to a terminal state on
 * {@code .settled}/{@code .failed}.
 */
@Entity
@Table(name = "payment_transfer_view")
public class PaymentTransferView {

    @Id
    private UUID transferId;

    @Column(nullable = false)
    private UUID customerId;

    private BigDecimal amount;
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransferReportStatus status;

    private String failureReason;

    @Column(nullable = false)
    private Instant requestedAt;

    private Instant settledAt;
    private Instant failedAt;

    protected PaymentTransferView() {
    }

    public PaymentTransferView(UUID transferId, UUID customerId, BigDecimal amount, String currency,
                                Instant requestedAt) {
        this.transferId = transferId;
        this.customerId = customerId;
        this.amount = amount;
        this.currency = currency;
        this.status = TransferReportStatus.PENDING;
        this.requestedAt = requestedAt;
    }

    public void settle(Instant settledAt) {
        this.status = TransferReportStatus.SETTLED;
        this.settledAt = settledAt;
    }

    public void fail(String reason, Instant failedAt) {
        this.status = TransferReportStatus.FAILED;
        this.failureReason = reason;
        this.failedAt = failedAt;
    }

    public UUID getTransferId() {
        return transferId;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public TransferReportStatus getStatus() {
        return status;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public Instant getSettledAt() {
        return settledAt;
    }

    public Instant getFailedAt() {
        return failedAt;
    }
}
