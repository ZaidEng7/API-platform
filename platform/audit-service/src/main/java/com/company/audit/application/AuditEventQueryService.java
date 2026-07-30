package com.company.audit.application;

import com.company.audit.domain.AuditEvent;
import com.company.audit.infrastructure.AuditEventJpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditEventQueryService {

    private final AuditEventJpaRepository auditEventRepository;

    public AuditEventQueryService(AuditEventJpaRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

    @Transactional(readOnly = true)
    public Page<AuditEvent> list(String eventType, Pageable pageable) {
        return eventType != null
                ? auditEventRepository.findByEventType(eventType, pageable)
                : auditEventRepository.findAll(pageable);
    }
}
