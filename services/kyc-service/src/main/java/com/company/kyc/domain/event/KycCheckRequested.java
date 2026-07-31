package com.company.kyc.domain.event;

import java.time.Instant;
import java.util.UUID;

/** Payload for {@code customer.kyc.requested} (guide §22 naming). */
public record KycCheckRequested(UUID checkId, UUID customerId, Instant requestedAt) {
}
