package com.company.investment.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record RequestSubscriptionRequest(
        @NotNull UUID customerId,
        @NotNull UUID ownerId,
        @NotNull UUID portfolioId,
        @NotBlank String fundCode,
        @NotNull @DecimalMin("0.000001") BigDecimal quantity) {
}
