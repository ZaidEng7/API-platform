package com.company.customer.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;

import java.time.LocalDate;

/**
 * Email and partyType are immutable via this endpoint — they're identity
 * fields for a Party, not profile details (changing either is a distinct,
 * out-of-scope business flow, not a plain field edit).
 */
public record UpdateCustomerRequest(
        @NotBlank String fullName,
        String phone,
        @Past LocalDate dateOfBirth) {
}
