package com.company.aml.api;

import com.company.aml.api.dto.RequestScreeningRequest;
import com.company.aml.api.dto.ScreeningFailureRequest;
import com.company.aml.api.dto.ScreeningResponse;
import com.company.aml.api.dto.ScreeningResultRequest;
import com.company.aml.api.mapper.ScreeningMapper;
import com.company.aml.application.AmlScreeningApplicationService;
import com.company.aml.domain.AmlScreening;
import com.company.platform.web.response.ApiResponse;
import com.company.platform.web.response.PageMeta;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * Async per guide §10.3's own example ({@code POST /aml/screenings} ->
 * {@code 202 Accepted}, poll {@code GET /aml/screenings/{id}} for status) —
 * never hold the HTTP connection open waiting on screening completion.
 * Role gates follow the realm's own role descriptions
 * (platform/identity/realm-export.json), same rationale as KYC Service's
 * controller: "compliance" owns the sign-off (here: the CLEAR/HIT result),
 * "operations" owns marking a screening technically FAILED.
 */
@Tag(name = "AML Screenings", description = "AML screening System of Record (guide §8.3)")
@RestController
@RequestMapping("/api/v1/aml/screenings")
public class ScreeningController {

    private final AmlScreeningApplicationService amlScreeningApplicationService;
    private final ScreeningMapper screeningMapper;

    public ScreeningController(AmlScreeningApplicationService amlScreeningApplicationService,
                                ScreeningMapper screeningMapper) {
        this.amlScreeningApplicationService = amlScreeningApplicationService;
        this.screeningMapper = screeningMapper;
    }

    @Operation(summary = "Get an AML screening by id")
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OPERATIONS', 'COMPLIANCE', 'CUSTOMER_SERVICE', 'AUDITOR')")
    public ApiResponse<ScreeningResponse> getById(@PathVariable UUID id) {
        return ApiResponse.of(screeningMapper.toResponse(amlScreeningApplicationService.getById(id)));
    }

    @Operation(summary = "List AML screenings for a customer")
    @GetMapping
    @PreAuthorize("hasAnyRole('OPERATIONS', 'COMPLIANCE', 'CUSTOMER_SERVICE', 'AUDITOR')")
    public ApiResponse<List<ScreeningResponse>> listByCustomer(
            @RequestParam UUID customerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 200), Sort.by("createdAt").descending());
        Page<AmlScreening> result = amlScreeningApplicationService.listByCustomer(customerId, pageable);

        List<ScreeningResponse> data = result.getContent().stream().map(screeningMapper::toResponse).toList();
        PageMeta meta = new PageMeta(result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
        return ApiResponse.of(data, meta);
    }

    @Operation(summary = "Request an AML screening for a customer (async — 202 Accepted, poll GET .../{id})")
    @PostMapping
    @PreAuthorize("hasAnyRole('OPERATIONS', 'COMPLIANCE', 'CUSTOMER_SERVICE')")
    public ResponseEntity<ApiResponse<ScreeningResponse>> requestScreening(@Valid @RequestBody RequestScreeningRequest request) {
        var screening = amlScreeningApplicationService.requestScreening(request.customerId());
        var response = screeningMapper.toResponse(screening);
        return ResponseEntity.accepted()
                .location(URI.create("/api/v1/aml/screenings/" + response.id()))
                .body(ApiResponse.of(response));
    }

    @Operation(summary = "Record a compliance result (CLEAR/HIT) on a screening, once only")
    @PostMapping("/{id}/result")
    @PreAuthorize("hasRole('COMPLIANCE')")
    public ApiResponse<ScreeningResponse> recordResult(@PathVariable UUID id, @Valid @RequestBody ScreeningResultRequest request) {
        var screening = amlScreeningApplicationService.complete(id, request.outcome(), request.notes());
        return ApiResponse.of(screeningMapper.toResponse(screening));
    }

    @Operation(summary = "Mark a screening technically failed (vendor/system failure, not a compliance outcome)")
    @PostMapping("/{id}/fail")
    @PreAuthorize("hasRole('OPERATIONS')")
    public ApiResponse<ScreeningResponse> recordFailure(@PathVariable UUID id, @Valid @RequestBody ScreeningFailureRequest request) {
        var screening = amlScreeningApplicationService.fail(id, request.reason());
        return ApiResponse.of(screeningMapper.toResponse(screening));
    }
}
