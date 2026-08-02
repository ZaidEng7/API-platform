package com.company.reporting.messaging.dto;

import java.time.Instant;
import java.util.UUID;

/** Mirrors AML Service's own {@code AmlScreeningRequested} event payload — {@code customer.aml.requested}. */
public record AmlScreeningRequestedPayload(UUID screeningId, UUID customerId, Instant requestedAt) {
}
