package com.company.reporting.messaging.dto;

import com.company.reporting.domain.KycCheckReportStatus;

import java.time.Instant;
import java.util.UUID;

/** Mirrors KYC Service's own {@code KycCheckDecided} event payload — {@code customer.kyc.approved}/{@code customer.kyc.rejected}. */
public record KycCheckDecidedPayload(UUID checkId, UUID customerId, KycCheckReportStatus status, String reason,
                                      String decidedBy, Instant decidedAt) {
}
