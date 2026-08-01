package com.company.reporting.api;

import com.company.reporting.api.dto.FundNavResponse;
import com.company.reporting.api.dto.PaymentTransferResponse;
import com.company.reporting.api.dto.PortfolioDetailResponse;
import com.company.reporting.api.dto.PortfolioResponse;
import com.company.reporting.api.dto.PositionResponse;
import com.company.reporting.api.mapper.ReportingMapper;
import com.company.reporting.application.PortfolioDetail;
import com.company.reporting.application.ReportingQueryService;
import com.company.reporting.domain.FundNavView;
import com.company.reporting.domain.PaymentTransferView;
import com.company.reporting.domain.PortfolioView;
import com.company.platform.web.response.ApiResponse;
import com.company.platform.web.response.PageMeta;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Read-only — no write endpoints exist here at all; every row is written by
 * {@link com.company.reporting.messaging.DomainEventReportingListener} off
 * a domain event, never via REST. Staff-only throughout: guide §8.3's own
 * SoR matrix lists "Reporting" and "Client Portal" as two separate read-copy
 * destinations for Portfolio positions — Reporting is the internal/back-office
 * surface, Client Portal (not built) would be the investor-facing one, so
 * there's deliberately no investor self-service ownership check here, unlike
 * Portfolio/Investment/Payment Service's own read endpoints.
 */
@RestController
@RequestMapping("/api/v1/reports")
public class ReportingController {

    private static final String READ_ROLES = "hasAnyRole('OPERATIONS', 'PORTFOLIO_MANAGER', 'AUDITOR', 'COMPLIANCE')";

    private final ReportingQueryService reportingQueryService;
    private final ReportingMapper reportingMapper;

    public ReportingController(ReportingQueryService reportingQueryService, ReportingMapper reportingMapper) {
        this.reportingQueryService = reportingQueryService;
        this.reportingMapper = reportingMapper;
    }

    @GetMapping("/funds")
    @PreAuthorize(READ_ROLES)
    public ApiResponse<List<FundNavResponse>> listFunds(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 200));
        Page<FundNavView> result = reportingQueryService.listFunds(pageable);

        List<FundNavResponse> data = result.getContent().stream().map(reportingMapper::toResponse).toList();
        PageMeta meta = new PageMeta(result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
        return ApiResponse.of(data, meta);
    }

    @GetMapping("/funds/{fundCode}")
    @PreAuthorize(READ_ROLES)
    public ApiResponse<FundNavResponse> getFund(@PathVariable String fundCode) {
        return ApiResponse.of(reportingMapper.toResponse(reportingQueryService.getFund(fundCode)));
    }

    @GetMapping("/portfolios")
    @PreAuthorize(READ_ROLES)
    public ApiResponse<List<PortfolioResponse>> listPortfolios(
            @RequestParam UUID ownerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 200));
        Page<PortfolioView> result = reportingQueryService.listPortfolios(ownerId, pageable);

        List<PortfolioResponse> data = result.getContent().stream().map(reportingMapper::toResponse).toList();
        PageMeta meta = new PageMeta(result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
        return ApiResponse.of(data, meta);
    }

    @GetMapping("/portfolios/{portfolioId}")
    @PreAuthorize(READ_ROLES)
    public ApiResponse<PortfolioDetailResponse> getPortfolio(@PathVariable UUID portfolioId) {
        PortfolioDetail detail = reportingQueryService.getPortfolioDetail(portfolioId);
        PortfolioView portfolio = detail.portfolio();
        List<PositionResponse> positions = detail.positions().stream().map(reportingMapper::toResponse).toList();
        return ApiResponse.of(new PortfolioDetailResponse(portfolio.getPortfolioId(), portfolio.getCustomerId(),
                portfolio.getOwnerId(), portfolio.getName(), portfolio.getCurrency(), portfolio.getOpenedAt(),
                positions));
    }

    @GetMapping("/payments")
    @PreAuthorize(READ_ROLES)
    public ApiResponse<List<PaymentTransferResponse>> listPayments(
            @RequestParam(required = false) UUID customerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 200));
        Page<PaymentTransferView> result = reportingQueryService.listPayments(customerId, pageable);

        List<PaymentTransferResponse> data = result.getContent().stream().map(reportingMapper::toResponse).toList();
        PageMeta meta = new PageMeta(result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
        return ApiResponse.of(data, meta);
    }
}
