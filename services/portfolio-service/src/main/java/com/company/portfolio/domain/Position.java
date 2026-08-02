package com.company.portfolio.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A holding within a {@link Portfolio} — keyed by {@code fundCode} (Fund
 * Service's own natural key, guide §8.3: "Fund / NAV ... Read copies
 * allowed in: Portfolio"), not a foreign key into Fund Service's database
 * (§8.1: services never share databases).
 */
@Entity
@Table(name = "positions")
public class Position {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID portfolioId;

    @Column(nullable = false)
    private String fundCode;

    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal quantity;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Opaque reference to whatever caused this position to be recorded
     * (Investment Service passes its {@code Subscription} id) — lets
     * {@link com.company.portfolio.application.PortfolioApplicationService#recordPosition}
     * detect a retried call and return the original position instead of
     * creating a duplicate. Nullable: not every caller necessarily has one.
     */
    @Column(updatable = false)
    private String sourceReference;

    protected Position() {
    }

    public Position(UUID id, UUID portfolioId, String fundCode, BigDecimal quantity, Instant createdAt,
                     String sourceReference) {
        this.id = id;
        this.portfolioId = portfolioId;
        this.fundCode = fundCode;
        this.quantity = quantity;
        this.createdAt = createdAt;
        this.sourceReference = sourceReference;
    }

    public UUID getId() {
        return id;
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

    public Instant getCreatedAt() {
        return createdAt;
    }
}
