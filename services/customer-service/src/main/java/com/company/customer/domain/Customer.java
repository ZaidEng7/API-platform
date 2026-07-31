package com.company.customer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "customers")
public class Customer {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String fullName;

    @Column(nullable = false, unique = true)
    private String email;

    private String phone;

    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PartyType partyType;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected Customer() {
    }

    public Customer(UUID id, String fullName, String email, String phone, LocalDate dateOfBirth,
                     PartyType partyType, Instant createdAt) {
        this.id = id;
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
        this.dateOfBirth = dateOfBirth;
        this.partyType = partyType;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public void update(String fullName, String phone, LocalDate dateOfBirth) {
        this.fullName = fullName;
        this.phone = phone;
        this.dateOfBirth = dateOfBirth;
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public PartyType getPartyType() {
        return partyType;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
