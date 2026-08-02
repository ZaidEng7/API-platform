package com.company.portfolio.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * {@code sourceReference} is optional but strongly recommended for any
 * caller that has a natural idempotency key of its own (e.g. Investment
 * Service passes its {@code Subscription} id) — see
 * {@code PortfolioApplicationService#recordPosition}'s Javadoc.
 */
public record RecordPositionRequest(@NotBlank String fundCode, @NotNull @DecimalMin("0.000001") BigDecimal quantity,
                                     String sourceReference) {
}
