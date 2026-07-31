package com.company.aml.domain.event;

import com.company.aml.domain.ScreeningOutcome;

import java.time.Instant;
import java.util.UUID;

/** Payload for {@code customer.aml.cleared}/{@code customer.aml.flagged} (guide §22 naming). */
public record AmlScreeningCompleted(UUID screeningId, UUID customerId, ScreeningOutcome outcome, String notes,
                                     Instant completedAt) {
}
