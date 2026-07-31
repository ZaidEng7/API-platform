package com.company.investment.api.dto;

import com.company.investment.domain.SubscriptionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record SubscriptionResponse(UUID id, UUID customerId, UUID ownerId, UUID portfolioId, String fundCode,
                                    BigDecimal quantity, SubscriptionStatus status, String failureReason,
                                    Instant createdAt, Instant updatedAt, Instant confirmedAt) {
}
