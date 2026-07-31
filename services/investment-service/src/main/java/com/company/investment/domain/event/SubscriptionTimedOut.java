package com.company.investment.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Payload for {@code investment.subscription.timed-out} — the dead-letter
 * signal guide §8.4 requires ("a stuck subscription must page someone,
 * not silently rot").
 */
public record SubscriptionTimedOut(UUID subscriptionId, UUID customerId, Instant timedOutAt) {
}
