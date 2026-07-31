package com.company.document.api.dto;

import jakarta.validation.constraints.NotBlank;

public record DocumentReviewRequest(@NotBlank String notes) {
}
