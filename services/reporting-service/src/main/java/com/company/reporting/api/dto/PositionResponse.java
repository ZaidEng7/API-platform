package com.company.reporting.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PositionResponse(UUID positionId, String fundCode, BigDecimal quantity, Instant recordedAt) {
}
