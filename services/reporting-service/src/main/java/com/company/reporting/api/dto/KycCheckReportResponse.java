package com.company.reporting.api.dto;

import com.company.reporting.domain.KycCheckReportStatus;

import java.time.Instant;
import java.util.UUID;

public record KycCheckReportResponse(UUID checkId, UUID customerId, KycCheckReportStatus status, String reason,
                                      String decidedBy, Instant requestedAt, Instant decidedAt) {
}
