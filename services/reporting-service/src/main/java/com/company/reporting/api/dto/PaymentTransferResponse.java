package com.company.reporting.api.dto;

import com.company.reporting.domain.TransferReportStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentTransferResponse(UUID transferId, UUID customerId, BigDecimal amount, String currency,
                                       TransferReportStatus status, String failureReason, Instant requestedAt,
                                       Instant settledAt, Instant failedAt) {
}
