package com.company.reporting.messaging.dto;

import java.time.Instant;
import java.util.UUID;

/** Mirrors Fund Service's own {@code FundRegistered} event payload — {@code fund.definition.registered}. */
public record FundRegisteredPayload(UUID fundId, String fundCode, String name, String currency, Instant registeredAt) {
}
