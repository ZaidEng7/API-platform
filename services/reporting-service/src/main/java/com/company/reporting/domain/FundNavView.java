package com.company.reporting.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Read copy of Fund Service's "Fund / NAV" data (guide §8.3 SoR matrix:
 * "Read copies allowed in: Portfolio, Reporting"). Maintained entirely by
 * {@code fund.definition.registered}/{@code fund.nav.updated} consumption —
 * never written via this service's own REST API. Keyed by {@code fundCode}
 * since {@code fund.nav.updated} doesn't carry a {@code fundId} (see Fund
 * Service's own event payload).
 */
@Entity
@Table(name = "fund_nav_view")
public class FundNavView {

    @Id
    private String fundCode;

    private String name;
    private String currency;
    private BigDecimal navPerShare;
    private LocalDate asOfDate;

    @Column(nullable = false)
    private Instant updatedAt;

    protected FundNavView() {
    }

    public FundNavView(String fundCode, Instant updatedAt) {
        this.fundCode = fundCode;
        this.updatedAt = updatedAt;
    }

    /** Applied on {@code fund.definition.registered} — leaves NAV fields untouched. */
    public void applyRegistration(String name, String currency, Instant registeredAt) {
        this.name = name;
        this.currency = currency;
        this.updatedAt = registeredAt;
    }

    /** Applied on {@code fund.nav.updated} — leaves name/currency untouched. */
    public void applyNavUpdate(BigDecimal navPerShare, LocalDate asOfDate, Instant fetchedAt) {
        this.navPerShare = navPerShare;
        this.asOfDate = asOfDate;
        this.updatedAt = fetchedAt;
    }

    public String getFundCode() {
        return fundCode;
    }

    public String getName() {
        return name;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getNavPerShare() {
        return navPerShare;
    }

    public LocalDate getAsOfDate() {
        return asOfDate;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
