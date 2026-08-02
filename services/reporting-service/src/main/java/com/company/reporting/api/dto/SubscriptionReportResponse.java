package com.company.reporting.api.dto;

import com.company.reporting.domain.SubscriptionReportStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record SubscriptionReportResponse(UUID subscriptionId, UUID customerId, UUID portfolioId, String fundCode,
                                          BigDecimal quantity, SubscriptionReportStatus status, String failureReason,
                                          Instant reservedAt, Instant confirmedAt, Instant cancelledAt,
                                          Instant failedAt, Instant timedOutAt) {
}
