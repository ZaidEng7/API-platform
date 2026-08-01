package com.company.reporting.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record FundNavResponse(String fundCode, String name, String currency, BigDecimal navPerShare,
                               LocalDate asOfDate, Instant updatedAt) {
}
