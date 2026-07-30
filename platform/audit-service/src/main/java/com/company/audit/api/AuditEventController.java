package com.company.audit.api;

import com.company.audit.api.dto.AuditEventResponse;
import com.company.audit.api.mapper.AuditEventMapper;
import com.company.audit.application.AuditEventQueryService;
import com.company.audit.domain.AuditEvent;
import com.company.platform.web.response.ApiResponse;
import com.company.platform.web.response.PageMeta;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only, restricted to Auditor/Compliance (guide §13: "access
 * restricted to Auditor/Compliance roles"). Enforced via
 * {@code @PreAuthorize}, which checks the current principal regardless of
 * whether the Gateway/filter-chain layer is itself enforcing auth yet —
 * see common-security's README for why that matters.
 */
@RestController
@RequestMapping("/api/v1/audit-events")
public class AuditEventController {

    private final AuditEventQueryService auditEventQueryService;
    private final AuditEventMapper auditEventMapper;

    public AuditEventController(AuditEventQueryService auditEventQueryService, AuditEventMapper auditEventMapper) {
        this.auditEventQueryService = auditEventQueryService;
        this.auditEventMapper = auditEventMapper;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('AUDITOR', 'COMPLIANCE')")
    public ApiResponse<List<AuditEventResponse>> list(
            @RequestParam(required = false) String eventType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 200), Sort.by("occurredAt").descending());
        Page<AuditEvent> result = auditEventQueryService.list(eventType, pageable);

        List<AuditEventResponse> data = result.getContent().stream().map(auditEventMapper::toResponse).toList();
        PageMeta meta = new PageMeta(result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
        return ApiResponse.of(data, meta);
    }
}
