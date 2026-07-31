package com.company.portfolio.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Payload for {@code portfolio.position.recorded} (guide §22 naming). */
public record PositionRecorded(UUID positionId, UUID portfolioId, String fundCode, BigDecimal quantity,
                                Instant recordedAt) {
}
