package com.company.reporting.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PortfolioDetailResponse(UUID portfolioId, UUID customerId, UUID ownerId, String name, String currency,
                                       Instant openedAt, List<PositionResponse> positions) {
}
