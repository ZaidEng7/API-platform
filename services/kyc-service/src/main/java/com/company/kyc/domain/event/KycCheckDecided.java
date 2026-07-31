package com.company.kyc.domain.event;

import com.company.kyc.domain.KycCheckStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Payload for {@code customer.kyc.approved}/{@code customer.kyc.rejected} —
 * the exact event names the guide's own §22 naming example uses.
 */
public record KycCheckDecided(UUID checkId, UUID customerId, KycCheckStatus status, String reason, String decidedBy,
                               Instant decidedAt) {
}
