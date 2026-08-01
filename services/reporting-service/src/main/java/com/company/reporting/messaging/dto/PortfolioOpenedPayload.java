package com.company.reporting.messaging.dto;

import java.time.Instant;
import java.util.UUID;

/** Mirrors Portfolio Service's own {@code PortfolioOpened} event payload — {@code portfolio.account.opened}. */
public record PortfolioOpenedPayload(UUID portfolioId, UUID customerId, UUID ownerId, String name, String currency,
                                      Instant openedAt) {
}
