package com.company.fund.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record RegisterFundRequest(
        @NotBlank String fundCode,
        @NotBlank String name,
        @NotBlank @Pattern(regexp = "[A-Z]{3}", message = "must be a 3-letter ISO 4217 currency code") String currency) {
}
