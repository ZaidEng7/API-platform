package com.company.kyc.api.dto;

import com.company.kyc.domain.KycCheckStatus;

import java.time.Instant;
import java.util.UUID;

public record KycCheckResponse(UUID id, UUID customerId, KycCheckStatus status, String reason, String decidedBy,
                                Instant createdAt, Instant updatedAt) {
}
