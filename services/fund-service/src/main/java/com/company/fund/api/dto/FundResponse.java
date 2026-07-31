package com.company.fund.api.dto;

import com.company.fund.domain.FundStatus;

import java.time.Instant;
import java.util.UUID;

public record FundResponse(UUID id, String fundCode, String name, String currency, FundStatus status,
                            Instant createdAt, Instant updatedAt) {
}
