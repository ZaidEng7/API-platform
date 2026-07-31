package com.company.portfolio.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PositionResponse(UUID id, UUID portfolioId, String fundCode, BigDecimal quantity, Instant createdAt) {
}
