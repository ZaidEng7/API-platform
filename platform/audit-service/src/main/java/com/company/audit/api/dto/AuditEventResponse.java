package com.company.audit.api.dto;

import java.time.Instant;
import java.util.UUID;

public record AuditEventResponse(
        UUID id,
        UUID sourceEventId,
        String eventType,
        String actor,
        Instant occurredAt,
        Instant recordedAt,
        String correlationId,
        String rawPayload) {
}
