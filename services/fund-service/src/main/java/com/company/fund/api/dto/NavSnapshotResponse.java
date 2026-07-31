package com.company.fund.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record NavSnapshotResponse(String fundCode, BigDecimal navPerShare, LocalDate asOfDate, Instant fetchedAt) {
}
