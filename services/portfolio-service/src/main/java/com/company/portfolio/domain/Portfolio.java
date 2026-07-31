package com.company.portfolio.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Guide §8.3 target SoR for "Portfolio positions" (interim SoR: "Portfolio
 * product", a legacy system with no adapter built yet). {@code customerId}
 * references Customer Service's Party by id only (§8.1). {@code ownerId}
 * is the investor's identity as asserted by the JWT ({@code sub} claim) —
 * this service treats it as directly comparable to
 * {@link com.company.platform.security.CurrentUser#subject()}, since no
 * Identity-to-Party linkage table exists anywhere in this platform yet to
 * build a richer mapping from. Ownership of this field is what guide
 * §12.2's object-level authorization rule enforces: "an Investor sees
 * *their* portfolio only... every {@code GET /portfolios/{id}} must
 * verify ownership".
 */
@Entity
@Table(name = "portfolios")
public class Portfolio {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID customerId;

    @Column(nullable = false)
    private UUID ownerId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PortfolioStatus status;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected Portfolio() {
    }

    public Portfolio(UUID id, UUID customerId, UUID ownerId, String name, String currency, Instant createdAt) {
        this.id = id;
        this.customerId = customerId;
        this.ownerId = ownerId;
        this.name = name;
        this.currency = currency;
        this.status = PortfolioStatus.ACTIVE;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public String getName() {
        return name;
    }

    public String getCurrency() {
        return currency;
    }

    public PortfolioStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
