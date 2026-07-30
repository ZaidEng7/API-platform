package com.company.audit.application;

import com.company.audit.domain.AuditEvent;
import com.company.audit.infrastructure.AuditEventJpaRepository;
import com.company.platform.messaging.idempotent.IdempotencyGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class AuditEventRecorder {

    private static final String CONSUMER_NAME = "audit-service";

    private final AuditEventJpaRepository auditEventRepository;
    private final IdempotencyGuard idempotencyGuard;

    public AuditEventRecorder(AuditEventJpaRepository auditEventRepository, IdempotencyGuard idempotencyGuard) {
        this.auditEventRepository = auditEventRepository;
        this.idempotencyGuard = idempotencyGuard;
    }

    /** @return true if a new record was written, false if sourceEventId was already processed */
    @Transactional
    public boolean record(UUID sourceEventId, String eventType, String actor, Instant occurredAt,
                           String correlationId, String rawPayload) {
        if (!idempotencyGuard.tryMarkProcessed(sourceEventId, CONSUMER_NAME)) {
            return false;
        }
        auditEventRepository.save(new AuditEvent(UUID.randomUUID(), sourceEventId, eventType, actor, occurredAt,
                correlationId, rawPayload));
        return true;
    }
}
