package com.company.investment.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Payload for {@code investment.subscription.confirmed} — the exact event
 * name the guide's own §22 naming example uses.
 */
public record SubscriptionConfirmed(UUID subscriptionId, UUID customerId, UUID portfolioId, String fundCode,
                                     BigDecimal quantity, Instant confirmedAt) {
}
