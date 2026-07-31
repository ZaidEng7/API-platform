package com.company.aml.api.dto;

import com.company.aml.domain.ScreeningOutcome;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ScreeningResultRequest(@NotNull ScreeningOutcome outcome, @NotBlank String notes) {
}
