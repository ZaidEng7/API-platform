package com.company.investment.api;

import com.company.investment.api.dto.RequestSubscriptionRequest;
import com.company.investment.api.dto.SubscriptionResponse;
import com.company.investment.api.mapper.SubscriptionMapper;
import com.company.investment.application.SubscriptionApplicationService;
import com.company.investment.domain.Subscription;
import com.company.platform.web.exception.ApiException;
import com.company.platform.web.response.ApiResponse;
import com.company.platform.web.response.PageMeta;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * Same role-gating pattern as Portfolio Service: staff broad, an
 * authenticated Investor restricted to their own ({@code ownerId})
 * subscriptions on reads. Writes are staff-only — no investor
 * self-service, same known limitation Portfolio Service documented.
 * {@code produces} declared explicitly — see {@code PortfolioController}'s
 * identical Javadoc for why (openapi-generator TypeScript clients silently
 * mis-parse JSON as a Blob without it).
 */
@Tag(name = "Subscriptions", description = "Fund subscription saga (guide §8.4)")
@RestController
@RequestMapping(value = "/api/v1/subscriptions", produces = MediaType.APPLICATION_JSON_VALUE)
public class SubscriptionController {

    private static final String READ_ROLES = "hasAnyRole('OPERATIONS', 'PORTFOLIO_MANAGER', 'AUDITOR', 'COMPLIANCE', 'INVESTOR')";
    private static final String WRITE_ROLES = "hasAnyRole('OPERATIONS', 'PORTFOLIO_MANAGER')";

    private final SubscriptionApplicationService subscriptionApplicationService;
    private final SubscriptionMapper subscriptionMapper;

    public SubscriptionController(SubscriptionApplicationService subscriptionApplicationService,
                                   SubscriptionMapper subscriptionMapper) {
        this.subscriptionApplicationService = subscriptionApplicationService;
        this.subscriptionMapper = subscriptionMapper;
    }

    @Operation(summary = "Get a subscription by id (ownership-enforced for an Investor caller)")
    @GetMapping("/{id}")
    @PreAuthorize(READ_ROLES)
    public ApiResponse<SubscriptionResponse> getById(@PathVariable UUID id) {
        return ApiResponse.of(subscriptionMapper.toResponse(subscriptionApplicationService.getById(id)));
    }

    @Operation(summary = "List subscriptions for an owner")
    @GetMapping
    @PreAuthorize(READ_ROLES)
    public ApiResponse<List<SubscriptionResponse>> listByOwner(
            @RequestParam UUID ownerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 200), Sort.by("createdAt").descending());
        Page<Subscription> result = subscriptionApplicationService.listByOwner(ownerId, pageable);

        List<SubscriptionResponse> data = result.getContent().stream().map(subscriptionMapper::toResponse).toList();
        PageMeta meta = new PageMeta(result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
        return ApiResponse.of(data, meta);
    }

    /**
     * Guide §12.3: POST endpoints creating financial effects require a
     * client-generated {@code Idempotency-Key} header. Validated here
     * (rather than relying on Spring's own missing-header handling, which
     * this codebase's shared {@code GlobalExceptionHandler} doesn't map to
     * a precise 400) so a missing key gets the same RFC 7807 shape as
     * every other validation failure.
     */
    @Operation(summary = "Request a fund subscription — runs the saga's synchronous validate/KYC/AML steps")
    @PostMapping
    @PreAuthorize(WRITE_ROLES)
    public ResponseEntity<ApiResponse<SubscriptionResponse>> requestSubscription(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody RequestSubscriptionRequest request) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Idempotency-Key header is required");
        }

        var subscription = subscriptionApplicationService.requestSubscription(idempotencyKey, request.customerId(),
                request.ownerId(), request.portfolioId(), request.fundCode(), request.quantity());
        var response = subscriptionMapper.toResponse(subscription);
        return ResponseEntity.created(URI.create("/api/v1/subscriptions/" + response.id()))
                .body(ApiResponse.of(response));
    }

    @Operation(summary = "Confirm payment on an awaiting-payment subscription, materializing the portfolio position")
    @PostMapping("/{id}/confirm-payment")
    @PreAuthorize(WRITE_ROLES)
    public ApiResponse<SubscriptionResponse> confirmPayment(@PathVariable UUID id) {
        return ApiResponse.of(subscriptionMapper.toResponse(subscriptionApplicationService.confirmPayment(id)));
    }

    @Operation(summary = "Cancel an awaiting-payment subscription (the saga's compensating action)")
    @PostMapping("/{id}/cancel")
    @PreAuthorize(WRITE_ROLES)
    public ApiResponse<SubscriptionResponse> cancel(@PathVariable UUID id) {
        return ApiResponse.of(subscriptionMapper.toResponse(subscriptionApplicationService.cancel(id)));
    }
}
