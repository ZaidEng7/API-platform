package com.company.reporting.messaging.dto;

import com.company.reporting.domain.AmlScreeningReportOutcome;

import java.time.Instant;
import java.util.UUID;

/** Mirrors AML Service's own {@code AmlScreeningCompleted} event payload — {@code customer.aml.cleared}/{@code customer.aml.flagged}. */
public record AmlScreeningCompletedPayload(UUID screeningId, UUID customerId, AmlScreeningReportOutcome outcome,
                                            String notes, Instant completedAt) {
}
