package com.company.fund.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Fund catalog entry — guide §8.3 lists this service as the target SoR for
 * "Fund / NAV", with the legacy Fund Management product as interim SoR,
 * reachable only through {@code integration/fund-mgmt-adapter} (guide §8.1:
 * services never access another service's, or another product's, database
 * directly). Fund existence/definition itself isn't sourced from that
 * adapter — it only exposes NAV lookup by an already-known fund code — so
 * registration here is staff-driven, same as Customer/KYC/AML/Document.
 */
@Entity
@Table(name = "funds")
public class Fund {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String fundCode;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FundStatus status;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected Fund() {
    }

    public Fund(UUID id, String fundCode, String name, String currency, Instant createdAt) {
        this.id = id;
        this.fundCode = fundCode;
        this.name = name;
        this.currency = currency;
        this.status = FundStatus.ACTIVE;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public UUID getId() {
        return id;
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

    public FundStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
