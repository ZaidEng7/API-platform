package com.company.payment.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Payload for {@code payment.transfer.requested} (guide §22 naming). */
public record TransferRequested(UUID transferId, UUID customerId, BigDecimal amount, String currency,
                                 Instant requestedAt) {
}
