package com.company.reporting.api.dto;

import com.company.reporting.domain.DocumentReportStatus;

import java.time.Instant;
import java.util.UUID;

public record DocumentReportResponse(UUID documentId, UUID customerId, String documentType,
                                      DocumentReportStatus status, String notes, Instant uploadedAt,
                                      Instant reviewedAt) {
}
