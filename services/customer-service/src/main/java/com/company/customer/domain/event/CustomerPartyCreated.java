package com.company.customer.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Payload for {@code customer.party.created} (guide §22 naming:
 * {@code <domain>.<entity>.<event-past-tense>}). "Party" matches the
 * guide's §8.3 System-of-Record terminology for this entity, not just
 * "Customer" — Customer Service is the target SoR for Party data.
 */
public record CustomerPartyCreated(UUID customerId, String fullName, String email, Instant createdAt) {
}
