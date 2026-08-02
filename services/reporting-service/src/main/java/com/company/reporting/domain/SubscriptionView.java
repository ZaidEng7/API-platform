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
 * Read copy of Investment Service's own "Subscription" (guide §8.3 SoR
 * matrix). Created on {@code investment.subscription.reserved} — or, if KYC/AML
 * validation fails at creation time and {@code reserved} never fires,
 * directly on {@code investment.subscription.failed} instead. A reserved
 * subscription's only further transitions are {@code confirmed},
 * {@code cancelled}, or {@code timed-out}; {@code failed} only ever happens
 * at creation time in place of {@code reserved}.
 */
@Entity
@Table(name = "subscription_view")
public class SubscriptionView {

    @Id
    private UUID subscriptionId;

    @Column(nullable = false)
    private UUID customerId;

    private UUID portfolioId;
    private String fundCode;
    private BigDecimal quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubscriptionReportStatus status;

    private String failureReason;

    private Instant reservedAt;
    private Instant confirmedAt;
    private Instant cancelledAt;
    private Instant failedAt;
    private Instant timedOutAt;

    protected SubscriptionView() {
    }

    public SubscriptionView(UUID subscriptionId, UUID customerId, UUID portfolioId, String fundCode,
                             BigDecimal quantity, Instant reservedAt) {
        this.subscriptionId = subscriptionId;
        this.customerId = customerId;
        this.portfolioId = portfolioId;
        this.fundCode = fundCode;
        this.quantity = quantity;
        this.status = SubscriptionReportStatus.AWAITING_PAYMENT;
        this.reservedAt = reservedAt;
    }

    public SubscriptionView(UUID subscriptionId, UUID customerId, Instant failedAt) {
        this.subscriptionId = subscriptionId;
        this.customerId = customerId;
        this.status = SubscriptionReportStatus.FAILED;
        this.failedAt = failedAt;
    }

    /**
     * Placeholder used only when an update-style event ({@code confirmed},
     * {@code cancelled}, {@code timed-out}) arrives before
     * {@code investment.subscription.reserved} was ever consumed — the
     * transition method called immediately afterward sets the real terminal
     * state before this row is ever persisted, so no status is assumed here.
     */
    public SubscriptionView(UUID subscriptionId, UUID customerId) {
        this.subscriptionId = subscriptionId;
        this.customerId = customerId;
    }

    public void confirm(Instant confirmedAt) {
        this.status = SubscriptionReportStatus.CONFIRMED;
        this.confirmedAt = confirmedAt;
    }

    public void cancel(Instant cancelledAt) {
        this.status = SubscriptionReportStatus.CANCELLED;
        this.cancelledAt = cancelledAt;
    }

    public void fail(String failureReason, Instant failedAt) {
        this.status = SubscriptionReportStatus.FAILED;
        this.failureReason = failureReason;
        this.failedAt = failedAt;
    }

    public void timeout(Instant timedOutAt) {
        this.status = SubscriptionReportStatus.TIMED_OUT;
        this.timedOutAt = timedOutAt;
    }

    public UUID getSubscriptionId() {
        return subscriptionId;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public UUID getPortfolioId() {
        return portfolioId;
    }

    public String getFundCode() {
        return fundCode;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public SubscriptionReportStatus getStatus() {
        return status;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getReservedAt() {
        return reservedAt;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public Instant getFailedAt() {
        return failedAt;
    }

    public Instant getTimedOutAt() {
        return timedOutAt;
    }
}
