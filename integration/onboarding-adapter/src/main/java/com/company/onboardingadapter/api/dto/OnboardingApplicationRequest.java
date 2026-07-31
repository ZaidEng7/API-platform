package com.company.onboardingadapter.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;

import java.time.LocalDate;

/** Canonical, business-language request shape — no legacy field names or date formats. */
public record OnboardingApplicationRequest(
        @NotBlank String fullName,
        @Email @NotBlank String email,
        @Past LocalDate dateOfBirth) {
}
