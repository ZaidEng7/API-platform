package com.company.reporting.api.dto;

import com.company.reporting.domain.AmlScreeningReportOutcome;
import com.company.reporting.domain.AmlScreeningReportStatus;

import java.time.Instant;
import java.util.UUID;

public record AmlScreeningReportResponse(UUID screeningId, UUID customerId, AmlScreeningReportStatus status,
                                          AmlScreeningReportOutcome outcome, String notes, String failureReason,
                                          Instant requestedAt, Instant completedAt, Instant failedAt) {
}
