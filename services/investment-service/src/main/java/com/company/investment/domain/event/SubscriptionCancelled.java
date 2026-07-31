package com.company.investment.domain.event;

import java.time.Instant;
import java.util.UUID;

/** Payload for {@code investment.subscription.cancelled} (guide §22 naming). */
public record SubscriptionCancelled(UUID subscriptionId, UUID customerId, Instant cancelledAt) {
}
