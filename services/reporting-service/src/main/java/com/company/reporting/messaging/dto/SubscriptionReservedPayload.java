package com.company.reporting.messaging.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Mirrors Investment Service's own {@code SubscriptionReserved} event payload — {@code investment.subscription.reserved}. */
public record SubscriptionReservedPayload(UUID subscriptionId, UUID customerId, UUID portfolioId, String fundCode,
                                           BigDecimal quantity, Instant reservedAt) {
}
