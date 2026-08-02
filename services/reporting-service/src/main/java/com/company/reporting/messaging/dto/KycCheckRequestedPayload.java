package com.company.reporting.messaging.dto;

import java.time.Instant;
import java.util.UUID;

/** Mirrors KYC Service's own {@code KycCheckRequested} event payload — {@code customer.kyc.requested}. */
public record KycCheckRequestedPayload(UUID checkId, UUID customerId, Instant requestedAt) {
}
