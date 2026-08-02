package com.company.kyc.api;

import com.company.kyc.api.dto.KycCheckResponse;
import com.company.kyc.api.dto.KycDecisionRequest;
import com.company.kyc.api.dto.RequestKycCheckRequest;
import com.company.kyc.api.mapper.KycCheckMapper;
import com.company.kyc.application.KycCheckApplicationService;
import com.company.kyc.domain.KycCheck;
import com.company.platform.web.response.ApiResponse;
import com.company.platform.web.response.PageMeta;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * Role gates follow the realm's own role descriptions
 * (platform/identity/realm-export.json): "compliance" is described as
 * "Compliance/AML/KYC review and sign-off" — the decision endpoint is
 * restricted to it alone. Reads are open to the staff roles that would
 * plausibly initiate/track one, plus 'investor' to check their own status
 * (ownership enforced in {@link KycCheckApplicationService}, same BOLA/IDOR
 * rule as Portfolio/Investment Service, guide §12.2). Requesting a check
 * stays staff-only — an investor doesn't self-initiate a KYC review.
 * {@code produces} declared explicitly — see {@code PortfolioController}'s
 * identical Javadoc for why (openapi-generator TypeScript clients silently
 * mis-parse JSON as a Blob without it).
 */
@Tag(name = "KYC Checks", description = "KYC status System of Record (guide §8.3)")
@RestController
@RequestMapping(value = "/api/v1/kyc-checks", produces = MediaType.APPLICATION_JSON_VALUE)
public class KycCheckController {

    private static final String READ_ROLES = "hasAnyRole('OPERATIONS', 'COMPLIANCE', 'CUSTOMER_SERVICE', 'AUDITOR', 'INVESTOR')";

    private final KycCheckApplicationService kycCheckApplicationService;
    private final KycCheckMapper kycCheckMapper;

    public KycCheckController(KycCheckApplicationService kycCheckApplicationService, KycCheckMapper kycCheckMapper) {
        this.kycCheckApplicationService = kycCheckApplicationService;
        this.kycCheckMapper = kycCheckMapper;
    }

    @Operation(summary = "Get a KYC check by id")
    @GetMapping("/{id}")
    @PreAuthorize(READ_ROLES)
    public ApiResponse<KycCheckResponse> getById(@PathVariable UUID id) {
        return ApiResponse.of(kycCheckMapper.toResponse(kycCheckApplicationService.getById(id)));
    }

    @Operation(summary = "List KYC checks for a customer")
    @GetMapping
    @PreAuthorize(READ_ROLES)
    public ApiResponse<List<KycCheckResponse>> listByCustomer(
            @RequestParam UUID customerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 200), Sort.by("createdAt").descending());
        Page<KycCheck> result = kycCheckApplicationService.listByCustomer(customerId, pageable);

        List<KycCheckResponse> data = result.getContent().stream().map(kycCheckMapper::toResponse).toList();
        PageMeta meta = new PageMeta(result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
        return ApiResponse.of(data, meta);
    }

    @Operation(summary = "Request a new KYC check for a customer")
    @PostMapping
    @PreAuthorize("hasAnyRole('OPERATIONS', 'COMPLIANCE', 'CUSTOMER_SERVICE')")
    public ResponseEntity<ApiResponse<KycCheckResponse>> requestCheck(@Valid @RequestBody RequestKycCheckRequest request) {
        var check = kycCheckApplicationService.requestCheck(request.customerId());
        var response = kycCheckMapper.toResponse(check);
        return ResponseEntity.created(URI.create("/api/v1/kyc-checks/" + response.id()))
                .body(ApiResponse.of(response));
    }

    @Operation(summary = "Record a compliance decision (approve/reject) on a KYC check, once only")
    @PostMapping("/{id}/decision")
    @PreAuthorize("hasRole('COMPLIANCE')")
    public ApiResponse<KycCheckResponse> decide(@PathVariable UUID id, @Valid @RequestBody KycDecisionRequest request) {
        var check = kycCheckApplicationService.decide(id, request.outcome(), request.reason());
        return ApiResponse.of(kycCheckMapper.toResponse(check));
    }
}
