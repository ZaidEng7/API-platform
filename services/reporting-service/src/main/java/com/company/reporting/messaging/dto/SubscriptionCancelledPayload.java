package com.company.reporting.messaging.dto;

import java.time.Instant;
import java.util.UUID;

/** Mirrors Investment Service's own {@code SubscriptionCancelled} event payload — {@code investment.subscription.cancelled}. */
public record SubscriptionCancelledPayload(UUID subscriptionId, UUID customerId, Instant cancelledAt) {
}
