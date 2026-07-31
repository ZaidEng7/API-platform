package com.company.investment.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Payload for {@code investment.subscription.reserved} (guide §22 naming). */
public record SubscriptionReserved(UUID subscriptionId, UUID customerId, UUID portfolioId, String fundCode,
                                    BigDecimal quantity, Instant reservedAt) {
}
