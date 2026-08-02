package com.company.reporting.messaging.dto;

import com.company.reporting.domain.DocumentReportStatus;

import java.time.Instant;
import java.util.UUID;

/** Mirrors Document Service's own {@code DocumentReviewed} event payload — {@code customer.document.verified}/{@code customer.document.rejected}. */
public record DocumentReviewedPayload(UUID documentId, UUID customerId, DocumentReportStatus status, String notes,
                                       Instant reviewedAt) {
}
