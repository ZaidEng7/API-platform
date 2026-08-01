package com.company.payment.api.dto;

import com.company.payment.domain.TransferStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransferResponse(UUID id, UUID customerId, UUID ownerId, BigDecimal amount, String currency,
                                String paymentMethodToken, String reference, TransferStatus status,
                                String failureReason, Instant createdAt, Instant updatedAt, Instant settledAt) {
}
