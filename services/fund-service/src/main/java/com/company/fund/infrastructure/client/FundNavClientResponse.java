package com.company.fund.infrastructure.client;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Mirrors {@code integration/fund-mgmt-adapter}'s own {@code FundNavResponse}
 * shape. Deliberately a separate type, not a shared dependency on the
 * adapter's DTO — services don't share domain types across their boundary
 * (guide §8.1), each side owns its own contract.
 */
public record FundNavClientResponse(String fundCode, BigDecimal navPerShare, LocalDate asOfDate) {
}
