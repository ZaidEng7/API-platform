package com.company.fund.api;

import com.company.fund.api.dto.FundResponse;
import com.company.fund.api.dto.NavSnapshotResponse;
import com.company.fund.api.dto.RegisterFundRequest;
import com.company.fund.api.mapper.FundMapper;
import com.company.fund.application.FundApplicationService;
import com.company.fund.domain.Fund;
import com.company.fund.domain.NavSnapshot;
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

/**
 * Role gates follow the realm's own role descriptions
 * (platform/identity/realm-export.json): "portfolio-manager" ("Investment/
 * portfolio management") is the natural fund-domain role, paired with
 * "operations" for administrative registration — same pairing pattern as
 * the other Phase 5 services' controllers.
 */
@Tag(name = "Funds", description = "Fund / NAV System of Record (guide §8.3)")
@RestController
@RequestMapping("/api/v1/funds")
public class FundController {

    private final FundApplicationService fundApplicationService;
    private final FundMapper fundMapper;

    public FundController(FundApplicationService fundApplicationService, FundMapper fundMapper) {
        this.fundApplicationService = fundApplicationService;
        this.fundMapper = fundMapper;
    }

    @Operation(summary = "Get a fund by its code")
    @GetMapping("/{fundCode}")
    @PreAuthorize("hasAnyRole('OPERATIONS', 'PORTFOLIO_MANAGER', 'AUDITOR')")
    public ApiResponse<FundResponse> getByCode(@PathVariable String fundCode) {
        return ApiResponse.of(fundMapper.toResponse(fundApplicationService.getByCode(fundCode)));
    }

    @Operation(summary = "List all registered funds")
    @GetMapping
    @PreAuthorize("hasAnyRole('OPERATIONS', 'PORTFOLIO_MANAGER', 'AUDITOR')")
    public ApiResponse<List<FundResponse>> listFunds(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 200), Sort.by("fundCode").ascending());
        Page<Fund> result = fundApplicationService.listFunds(pageable);

        List<FundResponse> data = result.getContent().stream().map(fundMapper::toResponse).toList();
        PageMeta meta = new PageMeta(result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
        return ApiResponse.of(data, meta);
    }

    @Operation(summary = "Register a new fund")
    @PostMapping
    @PreAuthorize("hasAnyRole('OPERATIONS', 'PORTFOLIO_MANAGER')")
    public ResponseEntity<ApiResponse<FundResponse>> registerFund(@Valid @RequestBody RegisterFundRequest request) {
        var fund = fundApplicationService.registerFund(request.fundCode(), request.name(), request.currency());
        var response = fundMapper.toResponse(fund);
        return ResponseEntity.created(URI.create("/api/v1/funds/" + response.fundCode()))
                .body(ApiResponse.of(response));
    }

    @Operation(summary = "Get the most recent NAV snapshot for a fund")
    @GetMapping("/{fundCode}/nav")
    @PreAuthorize("hasAnyRole('OPERATIONS', 'PORTFOLIO_MANAGER', 'AUDITOR')")
    public ApiResponse<NavSnapshotResponse> getLatestNav(@PathVariable String fundCode) {
        return ApiResponse.of(fundMapper.toResponse(fundApplicationService.getLatestNav(fundCode)));
    }

    @Operation(summary = "List historical NAV snapshots for a fund")
    @GetMapping("/{fundCode}/nav-history")
    @PreAuthorize("hasAnyRole('OPERATIONS', 'PORTFOLIO_MANAGER', 'AUDITOR')")
    public ApiResponse<List<NavSnapshotResponse>> getNavHistory(
            @PathVariable String fundCode,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 200), Sort.by("asOfDate").descending());
        Page<NavSnapshot> result = fundApplicationService.getNavHistory(fundCode, pageable);

        List<NavSnapshotResponse> data = result.getContent().stream().map(fundMapper::toResponse).toList();
        PageMeta meta = new PageMeta(result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
        return ApiResponse.of(data, meta);
    }

    @Operation(summary = "Refresh a fund's NAV on demand by calling fund-mgmt-adapter")
    @PostMapping("/{fundCode}/nav/refresh")
    @PreAuthorize("hasAnyRole('OPERATIONS', 'PORTFOLIO_MANAGER')")
    public ApiResponse<NavSnapshotResponse> refreshNav(@PathVariable String fundCode) {
        return ApiResponse.of(fundMapper.toResponse(fundApplicationService.refreshNav(fundCode)));
    }
}
