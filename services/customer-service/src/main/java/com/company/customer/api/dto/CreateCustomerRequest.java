package com.company.customer.api.dto;

import com.company.customer.domain.PartyType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;

import java.time.LocalDate;

/** {@code partyType} defaults to {@link PartyType#INDIVIDUAL} when omitted — most onboarding is retail. */
public record CreateCustomerRequest(
        @NotBlank String fullName,
        @NotBlank @Email String email,
        String phone,
        @Past LocalDate dateOfBirth,
        PartyType partyType) {
}
