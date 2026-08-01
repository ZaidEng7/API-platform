package com.company.reporting.messaging.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Mirrors Payment Service's own {@code TransferRequested} event payload — {@code payment.transfer.requested}. */
public record TransferRequestedPayload(UUID transferId, UUID customerId, BigDecimal amount, String currency,
                                        Instant requestedAt) {
}
