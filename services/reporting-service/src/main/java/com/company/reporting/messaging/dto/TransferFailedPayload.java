package com.company.reporting.messaging.dto;

import java.time.Instant;
import java.util.UUID;

/** Mirrors Payment Service's own {@code TransferFailed} event payload — {@code payment.transfer.failed}. */
public record TransferFailedPayload(UUID transferId, UUID customerId, String failureReason, Instant failedAt) {
}
