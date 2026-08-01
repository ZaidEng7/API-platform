package com.company.payment.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Payload for {@code payment.transfer.settled} — the exact event name the
 * guide's own §22 naming example uses.
 */
public record TransferSettled(UUID transferId, UUID customerId, BigDecimal amount, String currency,
                               Instant settledAt) {
}
