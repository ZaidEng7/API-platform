package com.company.aml.domain.event;

import java.time.Instant;
import java.util.UUID;

/** Payload for {@code customer.aml.requested} (guide §22 naming). */
public record AmlScreeningRequested(UUID screeningId, UUID customerId, Instant requestedAt) {
}
