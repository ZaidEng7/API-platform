package com.company.aml.domain.event;

import java.time.Instant;
import java.util.UUID;

/** Payload for {@code customer.aml.failed} — a technical/system failure, not a compliance HIT (guide §22 naming). */
public record AmlScreeningFailed(UUID screeningId, UUID customerId, String reason, Instant failedAt) {
}
