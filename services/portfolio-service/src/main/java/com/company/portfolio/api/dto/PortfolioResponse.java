package com.company.portfolio.api.dto;

import com.company.portfolio.domain.PortfolioStatus;

import java.time.Instant;
import java.util.UUID;

public record PortfolioResponse(UUID id, UUID customerId, UUID ownerId, String name, String currency,
                                 PortfolioStatus status, Instant createdAt, Instant updatedAt) {
}
