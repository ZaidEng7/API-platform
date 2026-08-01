package com.company.reporting.messaging.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Mirrors Payment Service's own {@code TransferSettled} event payload — {@code payment.transfer.settled}. */
public record TransferSettledPayload(UUID transferId, UUID customerId, BigDecimal amount, String currency,
                                      Instant settledAt) {
}
