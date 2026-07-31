package com.company.fund.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A NAV reading fetched from {@code integration/fund-mgmt-adapter}, at a
 * point in time. Keyed by {@code fundCode} (not a {@link Fund} foreign
 * key) — the adapter itself addresses funds by code, not by any internal
 * id this service owns, so that's the natural join key here too.
 */
@Entity
@Table(name = "nav_snapshots")
public class NavSnapshot {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String fundCode;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal navPerShare;

    @Column(nullable = false)
    private LocalDate asOfDate;

    @Column(nullable = false)
    private Instant fetchedAt;

    protected NavSnapshot() {
    }

    public NavSnapshot(UUID id, String fundCode, BigDecimal navPerShare, LocalDate asOfDate, Instant fetchedAt) {
        this.id = id;
        this.fundCode = fundCode;
        this.navPerShare = navPerShare;
        this.asOfDate = asOfDate;
        this.fetchedAt = fetchedAt;
    }

    public UUID getId() {
        return id;
    }

    public String getFundCode() {
        return fundCode;
    }

    public BigDecimal getNavPerShare() {
        return navPerShare;
    }

    public LocalDate getAsOfDate() {
        return asOfDate;
    }

    public Instant getFetchedAt() {
        return fetchedAt;
    }
}
