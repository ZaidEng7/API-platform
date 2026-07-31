package com.company.customer.api.dto;

import com.company.customer.domain.PartyType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record CustomerResponse(UUID id, String fullName, String email, String phone, LocalDate dateOfBirth,
                                PartyType partyType, Instant createdAt, Instant updatedAt) {
}
