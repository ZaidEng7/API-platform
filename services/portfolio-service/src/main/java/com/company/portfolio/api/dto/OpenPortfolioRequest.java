package com.company.portfolio.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

public record OpenPortfolioRequest(
        @NotNull UUID customerId,
        @NotNull UUID ownerId,
        @NotBlank String name,
        @NotBlank @Pattern(regexp = "[A-Z]{3}", message = "must be a 3-letter ISO 4217 currency code") String currency) {
}
