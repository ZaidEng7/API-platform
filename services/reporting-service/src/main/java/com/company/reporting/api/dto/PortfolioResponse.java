package com.company.reporting.api.dto;

import java.time.Instant;
import java.util.UUID;

public record PortfolioResponse(UUID portfolioId, UUID customerId, UUID ownerId, String name, String currency,
                                 Instant openedAt) {
}
