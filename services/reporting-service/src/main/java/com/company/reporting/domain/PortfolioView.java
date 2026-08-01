package com.company.reporting.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Read copy of Portfolio Service's "Portfolio positions" metadata (guide
 * §8.3 SoR matrix: "Read copies allowed in: Reporting, Client Portal" — this
 * service is the "Reporting" side; "Client Portal" is investor-facing and
 * not built). Written once on {@code portfolio.account.opened} and never
 * mutated afterward — positions are tracked separately in {@link PositionView}.
 */
@Entity
@Table(name = "portfolio_view")
public class PortfolioView {

    @Id
    private UUID portfolioId;

    @Column(nullable = false)
    private UUID customerId;

    @Column(nullable = false)
    private UUID ownerId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String currency;

    @Column(nullable = false)
    private Instant openedAt;

    protected PortfolioView() {
    }

    public PortfolioView(UUID portfolioId, UUID customerId, UUID ownerId, String name, String currency,
                          Instant openedAt) {
        this.portfolioId = portfolioId;
        this.customerId = customerId;
        this.ownerId = ownerId;
        this.name = name;
        this.currency = currency;
        this.openedAt = openedAt;
    }

    public UUID getPortfolioId() {
        return portfolioId;
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

    public Instant getOpenedAt() {
        return openedAt;
    }
}
