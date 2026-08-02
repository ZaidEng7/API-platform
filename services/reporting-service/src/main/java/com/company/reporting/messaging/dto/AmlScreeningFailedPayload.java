package com.company.reporting.messaging.dto;

import java.time.Instant;
import java.util.UUID;

/** Mirrors AML Service's own {@code AmlScreeningFailed} event payload — {@code customer.aml.failed}. */
public record AmlScreeningFailedPayload(UUID screeningId, UUID customerId, String reason, Instant failedAt) {
}
