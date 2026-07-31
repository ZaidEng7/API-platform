package com.company.investment.domain.event;

import java.time.Instant;
import java.util.UUID;

/** Payload for {@code investment.subscription.failed} (guide §22 naming). */
public record SubscriptionFailed(UUID subscriptionId, UUID customerId, String failureReason, Instant failedAt) {
}
