package com.company.audit.api.mapper;

import com.company.audit.api.dto.AuditEventResponse;
import com.company.audit.domain.AuditEvent;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AuditEventMapper {
    AuditEventResponse toResponse(AuditEvent auditEvent);
}
