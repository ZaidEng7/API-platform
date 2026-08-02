package com.company.portfolio.api;

import com.company.portfolio.api.dto.OpenPortfolioRequest;
import com.company.portfolio.api.dto.PortfolioResponse;
import com.company.portfolio.api.dto.PortfolioValuationResponse;
import com.company.portfolio.api.dto.PositionResponse;
import com.company.portfolio.api.dto.RecordPositionRequest;
import com.company.portfolio.api.mapper.PortfolioMapper;
import com.company.portfolio.application.PortfolioApplicationService;
import com.company.portfolio.domain.Portfolio;
import com.company.portfolio.domain.Position;
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
 * Coarse-grained role gate here (any staff role, or an authenticated
 * Investor) via {@code @PreAuthorize}; fine-grained per-resource ownership
 * (guide §12.2 — an Investor sees *their* portfolio only) is enforced in
 * {@link PortfolioApplicationService}, per the layering
 * {@link com.company.platform.security.CurrentUser}'s own Javadoc
 * describes. Writes are staff-only — no investor self-service flow yet.
 * {@code produces} is declared explicitly so springdoc documents
 * {@code application/json} (not the default wildcard media type) —
 * without it, openapi-generator's TypeScript client sets Angular's
 * HttpClient {@code responseType: 'blob'} instead of {@code 'json'} (its
 * {@code isJsonMime} check only matches an explicit JSON media type),
 * silently breaking every generated client's response parsing even though
 * the actual runtime response genuinely is JSON.
 */
@Tag(name = "Portfolios", description = "Portfolio positions System of Record (guide §8.3)")
@RestController
@RequestMapping(value = "/api/v1/portfolios", produces = MediaType.APPLICATION_JSON_VALUE)
public class PortfolioController {

    private static final String READ_ROLES = "hasAnyRole('OPERATIONS', 'PORTFOLIO_MANAGER', 'AUDITOR', 'COMPLIANCE', 'INVESTOR')";
    private static final String WRITE_ROLES = "hasAnyRole('OPERATIONS', 'PORTFOLIO_MANAGER')";

    private final PortfolioApplicationService portfolioApplicationService;
    private final PortfolioMapper portfolioMapper;

    public PortfolioController(PortfolioApplicationService portfolioApplicationService, PortfolioMapper portfolioMapper) {
        this.portfolioApplicationService = portfolioApplicationService;
        this.portfolioMapper = portfolioMapper;
    }

    @Operation(summary = "Get a portfolio by id (ownership-enforced for an Investor caller)")
    @GetMapping("/{id}")
    @PreAuthorize(READ_ROLES)
    public ApiResponse<PortfolioResponse> getById(@PathVariable UUID id) {
        return ApiResponse.of(portfolioMapper.toResponse(portfolioApplicationService.getById(id)));
    }

    @Operation(summary = "List portfolios for an owner")
    @GetMapping
    @PreAuthorize(READ_ROLES)
    public ApiResponse<List<PortfolioResponse>> listByOwner(
            @RequestParam UUID ownerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 200), Sort.by("createdAt").descending());
        Page<Portfolio> result = portfolioApplicationService.listByOwner(ownerId, pageable);

        List<PortfolioResponse> data = result.getContent().stream().map(portfolioMapper::toResponse).toList();
        PageMeta meta = new PageMeta(result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
        return ApiResponse.of(data, meta);
    }

    @Operation(summary = "Open a new portfolio account")
    @PostMapping
    @PreAuthorize(WRITE_ROLES)
    public ResponseEntity<ApiResponse<PortfolioResponse>> openPortfolio(@Valid @RequestBody OpenPortfolioRequest request) {
        var portfolio = portfolioApplicationService.openPortfolio(request.customerId(), request.ownerId(),
                request.name(), request.currency());
        var response = portfolioMapper.toResponse(portfolio);
        return ResponseEntity.created(URI.create("/api/v1/portfolios/" + response.id()))
                .body(ApiResponse.of(response));
    }

    @Operation(summary = "List positions held in a portfolio")
    @GetMapping("/{id}/positions")
    @PreAuthorize(READ_ROLES)
    public ApiResponse<List<PositionResponse>> listPositions(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 200), Sort.by("createdAt").descending());
        Page<Position> result = portfolioApplicationService.listPositions(id, pageable);

        List<PositionResponse> data = result.getContent().stream().map(portfolioMapper::toResponse).toList();
        PageMeta meta = new PageMeta(result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
        return ApiResponse.of(data, meta);
    }

    @Operation(summary = "Record a position (fund holding) into a portfolio")
    @PostMapping("/{id}/positions")
    @PreAuthorize(WRITE_ROLES)
    public ResponseEntity<ApiResponse<PositionResponse>> recordPosition(@PathVariable UUID id,
                                                                         @Valid @RequestBody RecordPositionRequest request) {
        var position = portfolioApplicationService.recordPosition(id, request.fundCode(), request.quantity(),
                request.sourceReference());
        var response = portfolioMapper.toResponse(position);
        return ResponseEntity.created(URI.create("/api/v1/portfolios/" + id + "/positions/" + response.id()))
                .body(ApiResponse.of(response));
    }

    @Operation(summary = "Value a portfolio by pricing each held position against Fund Service's latest NAV")
    @GetMapping("/{id}/valuation")
    @PreAuthorize(READ_ROLES)
    public ApiResponse<PortfolioValuationResponse> valuate(@PathVariable UUID id) {
        return ApiResponse.of(portfolioApplicationService.valuate(id));
    }
}
