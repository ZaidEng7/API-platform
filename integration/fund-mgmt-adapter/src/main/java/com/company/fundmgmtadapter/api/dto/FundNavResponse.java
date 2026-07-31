package com.company.fundmgmtadapter.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Canonical, business-language shape — a real decimal, not a legacy scaled integer. */
public record FundNavResponse(String fundCode, BigDecimal navPerShare, LocalDate asOfDate) {
}
