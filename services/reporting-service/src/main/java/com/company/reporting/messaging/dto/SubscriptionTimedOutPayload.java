package com.company.reporting.messaging.dto;

import java.time.Instant;
import java.util.UUID;

/** Mirrors Investment Service's own {@code SubscriptionTimedOut} event payload — {@code investment.subscription.timed-out}. */
public record SubscriptionTimedOutPayload(UUID subscriptionId, UUID customerId, Instant timedOutAt) {
}
