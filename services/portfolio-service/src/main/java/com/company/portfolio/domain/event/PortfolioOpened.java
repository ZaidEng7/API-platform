package com.company.portfolio.domain.event;

import java.time.Instant;
import java.util.UUID;

/** Payload for {@code portfolio.account.opened} (guide §22 naming). */
public record PortfolioOpened(UUID portfolioId, UUID customerId, UUID ownerId, String name, String currency,
                               Instant openedAt) {
}
