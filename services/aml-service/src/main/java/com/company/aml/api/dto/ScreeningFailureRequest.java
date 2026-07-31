package com.company.aml.api.dto;

import jakarta.validation.constraints.NotBlank;

public record ScreeningFailureRequest(@NotBlank String reason) {
}
