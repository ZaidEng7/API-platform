package com.company.reporting.messaging.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Mirrors Portfolio Service's own {@code PositionRecorded} event payload — {@code portfolio.position.recorded}. */
public record PositionRecordedPayload(UUID positionId, UUID portfolioId, String fundCode, BigDecimal quantity,
                                       Instant recordedAt) {
}
