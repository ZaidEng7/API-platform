package com.company.portfolio.api.dto;

import java.math.BigDecimal;

public record PositionValuation(String fundCode, BigDecimal quantity, BigDecimal navPerShare, BigDecimal marketValue) {
}
