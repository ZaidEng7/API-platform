package com.company.audit.infrastructure;

import com.company.audit.domain.AuditEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** Append-only: no update/delete methods are exposed on purpose (guide §13). */
public interface AuditEventJpaRepository extends JpaRepository<AuditEvent, UUID> {

    boolean existsBySourceEventId(UUID sourceEventId);

    Page<AuditEvent> findByEventType(String eventType, Pageable pageable);
}
