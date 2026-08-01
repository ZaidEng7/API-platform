package com.company.reporting.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Read copy of one {@code portfolio.position.recorded} event. Append-only —
 * this service doesn't attempt to net multiple records into a single
 * running position; that aggregation belongs to Portfolio Service itself
 * (the actual System of Record), not its read copy.
 */
@Entity
@Table(name = "position_view")
public class PositionView {

    @Id
    private UUID positionId;

    @Column(nullable = false)
    private UUID portfolioId;

    @Column(nullable = false)
    private String fundCode;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal quantity;

    @Column(nullable = false)
    private Instant recordedAt;

    protected PositionView() {
    }

    public PositionView(UUID positionId, UUID portfolioId, String fundCode, BigDecimal quantity, Instant recordedAt) {
        this.positionId = positionId;
        this.portfolioId = portfolioId;
        this.fundCode = fundCode;
        this.quantity = quantity;
        this.recordedAt = recordedAt;
    }

    public UUID getPositionId() {
        return positionId;
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

    public Instant getRecordedAt() {
        return recordedAt;
    }
}
