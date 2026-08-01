package com.company.reporting.messaging.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Mirrors Fund Service's own {@code FundNavUpdated} event payload — {@code fund.nav.updated}. */
public record FundNavUpdatedPayload(String fundCode, BigDecimal navPerShare, LocalDate asOfDate, Instant fetchedAt) {
}
