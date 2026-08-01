package com.company.payment.api.dto;

import jakarta.validation.constraints.NotBlank;

public record TransferFailureRequest(@NotBlank String reason) {
}
