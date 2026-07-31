package com.company.fund.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Payload for {@code fund.nav.updated} (guide §22 naming). */
public record FundNavUpdated(String fundCode, BigDecimal navPerShare, LocalDate asOfDate, Instant fetchedAt) {
}
