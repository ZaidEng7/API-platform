package com.company.portfolio.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * All positions are assumed to already be denominated in {@code currency}
 * — no FX conversion (no FX-rate source exists in this platform yet).
 */
public record PortfolioValuationResponse(UUID portfolioId, String currency, List<PositionValuation> positions,
                                          BigDecimal totalValue, Instant valuedAt) {
}
