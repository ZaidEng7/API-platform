package com.company.reporting.messaging.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Mirrors Investment Service's own {@code SubscriptionConfirmed} event payload — {@code investment.subscription.confirmed}. */
public record SubscriptionConfirmedPayload(UUID subscriptionId, UUID customerId, UUID portfolioId, String fundCode,
                                            BigDecimal quantity, Instant confirmedAt) {
}
