package com.company.reporting.messaging.dto;

import java.time.Instant;
import java.util.UUID;

/** Mirrors Investment Service's own {@code SubscriptionFailed} event payload — {@code investment.subscription.failed}. */
public record SubscriptionFailedPayload(UUID subscriptionId, UUID customerId, String failureReason, Instant failedAt) {
}
