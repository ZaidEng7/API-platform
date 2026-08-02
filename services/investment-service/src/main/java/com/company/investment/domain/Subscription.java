package com.company.investment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A fund subscription — the orchestrated saga guide §8.4 names explicitly
 * ("validate customer → KYC/AML check → reserve units → collect payment →
 * confirm → notify"), driven by this service rather than a distributed
 * 2PC transaction. Persisted here per §8.4 ("the owning service...
 * persists saga state"). {@code customerId}/{@code ownerId}/
 * {@code portfolioId}/{@code fundCode} are all references into other
 * services' own data (§8.1 — no shared databases), the same pattern
 * Portfolio Service established for {@code ownerId}.
 *
 * <p>"Reserve units" here means this record existing in
 * {@code AWAITING_PAYMENT} — there's no separate fund-inventory concept
 * to hold against (Fund Service tracks NAV pricing, not a finite unit
 * supply; mutual funds create units on subscription rather than drawing
 * from a fixed pool). The position is only actually materialized in
 * Portfolio Service once payment is confirmed.
 */
@Entity
@Table(name = "subscriptions")
public class Subscription {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String idempotencyKey;

    @Column(nullable = false)
    private UUID customerId;

    @Column(nullable = false)
    private UUID ownerId;

    @Column(nullable = false)
    private UUID portfolioId;

    @Column(nullable = false)
    private String fundCode;

    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubscriptionStatus status;

    private String failureReason;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    private Instant confirmedAt;

    /** Only meaningful while {@code status == AWAITING_PAYMENT} — when the timeout job should act. */
    private Instant timeoutAt;

    /**
     * Optimistic lock: {@link com.company.investment.infrastructure.scheduling.SubscriptionTimeoutJob}
     * and {@code confirmPayment()}/{@code cancel()} can all race on the
     * same AWAITING_PAYMENT row from independent transactions. Without
     * this, JPA's default save() silently overwrites whichever transaction
     * commits last — this makes a lost race throw
     * {@code OptimisticLockingFailureException} instead.
     */
    @Version
    private long version;

    protected Subscription() {
    }

    private Subscription(UUID id, String idempotencyKey, UUID customerId, UUID ownerId, UUID portfolioId,
                          String fundCode, BigDecimal quantity, SubscriptionStatus status, String failureReason,
                          Instant createdAt, Instant timeoutAt) {
        this.id = id;
        this.idempotencyKey = idempotencyKey;
        this.customerId = customerId;
        this.ownerId = ownerId;
        this.portfolioId = portfolioId;
        this.fundCode = fundCode;
        this.quantity = quantity;
        this.status = status;
        this.failureReason = failureReason;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
        this.timeoutAt = timeoutAt;
    }

    /** The saga's "reserve units" step succeeded — validation, KYC, and AML checks all passed. */
    public static Subscription reserved(UUID id, String idempotencyKey, UUID customerId, UUID ownerId,
                                         UUID portfolioId, String fundCode, BigDecimal quantity, Instant createdAt,
                                         Instant timeoutAt) {
        return new Subscription(id, idempotencyKey, customerId, ownerId, portfolioId, fundCode, quantity,
                SubscriptionStatus.AWAITING_PAYMENT, null, createdAt, timeoutAt);
    }

    /** The saga failed before reservation — a terminal state from the start, not a transition. */
    public static Subscription failed(UUID id, String idempotencyKey, UUID customerId, UUID ownerId,
                                       UUID portfolioId, String fundCode, BigDecimal quantity, String failureReason,
                                       Instant createdAt) {
        return new Subscription(id, idempotencyKey, customerId, ownerId, portfolioId, fundCode, quantity,
                SubscriptionStatus.FAILED, failureReason, createdAt, null);
    }

    /** @throws IllegalStateException if this subscription isn't AWAITING_PAYMENT. */
    public void confirm() {
        requireAwaitingPayment();
        this.status = SubscriptionStatus.CONFIRMED;
        this.updatedAt = Instant.now();
        this.confirmedAt = this.updatedAt;
    }

    /**
     * The compensating action for a subscription that never gets paid —
     * releasing the "reservation" is exactly this transition, since there
     * was never any separate inventory hold to undo (see class Javadoc).
     *
     * @throws IllegalStateException if this subscription isn't AWAITING_PAYMENT.
     */
    public void cancel() {
        requireAwaitingPayment();
        this.status = SubscriptionStatus.CANCELLED;
        this.updatedAt = Instant.now();
    }

    /** @throws IllegalStateException if this subscription isn't AWAITING_PAYMENT. */
    public void timeout() {
        requireAwaitingPayment();
        this.status = SubscriptionStatus.TIMED_OUT;
        this.updatedAt = Instant.now();
    }

    private void requireAwaitingPayment() {
        if (this.status != SubscriptionStatus.AWAITING_PAYMENT) {
            throw new IllegalStateException("Subscription " + id + " is not awaiting payment (" + this.status + ")");
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

    public UUID getPortfolioId() {
        return portfolioId;
    }

    public String getFundCode() {
        return fundCode;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public SubscriptionStatus getStatus() {
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

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public Instant getTimeoutAt() {
        return timeoutAt;
    }
}
