package com.company.payment.domain.event;

import java.time.Instant;
import java.util.UUID;

/** Payload for {@code payment.transfer.failed} (guide §22 naming). */
public record TransferFailed(UUID transferId, UUID customerId, String failureReason, Instant failedAt) {
}
