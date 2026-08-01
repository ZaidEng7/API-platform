package com.company.payment.api;

import com.company.payment.api.dto.RequestTransferRequest;
import com.company.payment.api.dto.TransferFailureRequest;
import com.company.payment.api.dto.TransferResponse;
import com.company.payment.api.mapper.TransferMapper;
import com.company.payment.application.TransferApplicationService;
import com.company.payment.domain.Transfer;
import com.company.platform.web.exception.ApiException;
import com.company.platform.web.response.ApiResponse;
import com.company.platform.web.response.PageMeta;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * Async per guide §10.3's own example — {@code POST /api/v1/payments}
 * returns {@code 202 Accepted}, poll {@code GET .../{id}} for status —
 * same pattern AML Service established. Financial-effect POST requires an
 * {@code Idempotency-Key} header (guide §12.3), same pattern Investment
 * Service established. Role gates mirror Portfolio/Investment Service:
 * staff broad, an authenticated Investor restricted to their own
 * ({@code ownerId}) transfers on reads.
 */
@RestController
@RequestMapping("/api/v1/payments")
public class TransferController {

    private static final String READ_ROLES = "hasAnyRole('OPERATIONS', 'PORTFOLIO_MANAGER', 'AUDITOR', 'COMPLIANCE', 'INVESTOR')";
    private static final String WRITE_ROLES = "hasAnyRole('OPERATIONS', 'PORTFOLIO_MANAGER')";

    private final TransferApplicationService transferApplicationService;
    private final TransferMapper transferMapper;

    public TransferController(TransferApplicationService transferApplicationService, TransferMapper transferMapper) {
        this.transferApplicationService = transferApplicationService;
        this.transferMapper = transferMapper;
    }

    @GetMapping("/{id}")
    @PreAuthorize(READ_ROLES)
    public ApiResponse<TransferResponse> getById(@PathVariable UUID id) {
        return ApiResponse.of(transferMapper.toResponse(transferApplicationService.getById(id)));
    }

    @GetMapping
    @PreAuthorize(READ_ROLES)
    public ApiResponse<List<TransferResponse>> listByOwner(
            @RequestParam UUID ownerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 200), Sort.by("createdAt").descending());
        Page<Transfer> result = transferApplicationService.listByOwner(ownerId, pageable);

        List<TransferResponse> data = result.getContent().stream().map(transferMapper::toResponse).toList();
        PageMeta meta = new PageMeta(result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
        return ApiResponse.of(data, meta);
    }

    /**
     * Guide §12.3: POST endpoints creating financial effects require a
     * client-generated {@code Idempotency-Key} header. Validated here
     * (rather than relying on Spring's own missing-header handling, which
     * this codebase's shared {@code GlobalExceptionHandler} doesn't map to
     * a precise 400) so a missing key gets the same RFC 7807 shape as
     * every other validation failure — same approach Investment Service
     * established.
     */
    @PostMapping
    @PreAuthorize(WRITE_ROLES)
    public ResponseEntity<ApiResponse<TransferResponse>> requestTransfer(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody RequestTransferRequest request) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Idempotency-Key header is required");
        }

        var transfer = transferApplicationService.requestTransfer(idempotencyKey, request.customerId(),
                request.ownerId(), request.amount(), request.currency(), request.paymentMethodToken(),
                request.reference());
        var response = transferMapper.toResponse(transfer);
        return ResponseEntity.accepted()
                .location(URI.create("/api/v1/payments/" + response.id()))
                .body(ApiResponse.of(response));
    }

    @PostMapping("/{id}/settle")
    @PreAuthorize("hasRole('OPERATIONS')")
    public ApiResponse<TransferResponse> settle(@PathVariable UUID id) {
        return ApiResponse.of(transferMapper.toResponse(transferApplicationService.settle(id)));
    }

    @PostMapping("/{id}/fail")
    @PreAuthorize("hasRole('OPERATIONS')")
    public ApiResponse<TransferResponse> fail(@PathVariable UUID id, @Valid @RequestBody TransferFailureRequest request) {
        return ApiResponse.of(transferMapper.toResponse(transferApplicationService.fail(id, request.reason())));
    }
}
