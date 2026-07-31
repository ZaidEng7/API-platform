package com.company.aml.api.dto;

import com.company.aml.domain.ScreeningOutcome;
import com.company.aml.domain.ScreeningStatus;

import java.time.Instant;
import java.util.UUID;

public record ScreeningResponse(UUID id, UUID customerId, ScreeningStatus status, ScreeningOutcome outcome,
                                 String notes, Instant createdAt, Instant updatedAt, Instant completedAt) {
}
