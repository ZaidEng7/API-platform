package com.company.fund.domain.event;

import java.time.Instant;
import java.util.UUID;

/** Payload for {@code fund.definition.registered} (guide §22 naming). */
public record FundRegistered(UUID fundId, String fundCode, String name, String currency, Instant registeredAt) {
}
