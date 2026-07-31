package com.company.portfolio.infrastructure.client;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Mirrors Fund Service's own {@code NavSnapshotResponse} shape (just the
 * fields this client needs). Deliberately a separate type, not a shared
 * dependency on Fund Service's DTO — services don't share domain types
 * across their boundary (guide §8.1).
 */
public record FundNavClientResponse(String fundCode, BigDecimal navPerShare, LocalDate asOfDate) {
}
