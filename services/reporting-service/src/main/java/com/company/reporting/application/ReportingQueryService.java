package com.company.reporting.application;

import com.company.reporting.domain.FundNavView;
import com.company.reporting.domain.PaymentTransferView;
import com.company.reporting.domain.PortfolioView;
import com.company.reporting.infrastructure.FundNavViewJpaRepository;
import com.company.reporting.infrastructure.PaymentTransferViewJpaRepository;
import com.company.reporting.infrastructure.PortfolioViewJpaRepository;
import com.company.reporting.infrastructure.PositionViewJpaRepository;
import com.company.platform.web.exception.ApiException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Read-only by design — every mutation happens in {@link ReportingIngestionService}
 * off the back of a domain event, never via this service's own REST API.
 */
@Service
public class ReportingQueryService {

    private final FundNavViewJpaRepository fundNavViewRepository;
    private final PortfolioViewJpaRepository portfolioViewRepository;
    private final PositionViewJpaRepository positionViewRepository;
    private final PaymentTransferViewJpaRepository paymentTransferViewRepository;

    public ReportingQueryService(FundNavViewJpaRepository fundNavViewRepository,
                                  PortfolioViewJpaRepository portfolioViewRepository,
                                  PositionViewJpaRepository positionViewRepository,
                                  PaymentTransferViewJpaRepository paymentTransferViewRepository) {
        this.fundNavViewRepository = fundNavViewRepository;
        this.portfolioViewRepository = portfolioViewRepository;
        this.positionViewRepository = positionViewRepository;
        this.paymentTransferViewRepository = paymentTransferViewRepository;
    }

    @Transactional(readOnly = true)
    public Page<FundNavView> listFunds(Pageable pageable) {
        return fundNavViewRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public FundNavView getFund(String fundCode) {
        return fundNavViewRepository.findById(fundCode)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "RPT-4041", "No NAV data for fund: " + fundCode));
    }

    @Transactional(readOnly = true)
    public Page<PortfolioView> listPortfolios(UUID ownerId, Pageable pageable) {
        return portfolioViewRepository.findByOwnerId(ownerId, pageable);
    }

    @Transactional(readOnly = true)
    public PortfolioDetail getPortfolioDetail(UUID portfolioId) {
        PortfolioView portfolio = portfolioViewRepository.findById(portfolioId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "RPT-4042", "No portfolio data for: " + portfolioId));
        return new PortfolioDetail(portfolio, positionViewRepository.findByPortfolioId(portfolioId));
    }

    @Transactional(readOnly = true)
    public Page<PaymentTransferView> listPayments(UUID customerId, Pageable pageable) {
        return customerId != null
                ? paymentTransferViewRepository.findByCustomerId(customerId, pageable)
                : paymentTransferViewRepository.findAll(pageable);
    }
}
