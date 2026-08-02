package com.company.reporting.application;

import com.company.reporting.domain.AmlScreeningView;
import com.company.reporting.domain.DocumentView;
import com.company.reporting.domain.FundNavView;
import com.company.reporting.domain.KycCheckView;
import com.company.reporting.domain.PaymentTransferView;
import com.company.reporting.domain.PortfolioView;
import com.company.reporting.domain.SubscriptionView;
import com.company.reporting.infrastructure.AmlScreeningViewJpaRepository;
import com.company.reporting.infrastructure.DocumentViewJpaRepository;
import com.company.reporting.infrastructure.FundNavViewJpaRepository;
import com.company.reporting.infrastructure.KycCheckViewJpaRepository;
import com.company.reporting.infrastructure.PaymentTransferViewJpaRepository;
import com.company.reporting.infrastructure.PortfolioViewJpaRepository;
import com.company.reporting.infrastructure.PositionViewJpaRepository;
import com.company.reporting.infrastructure.SubscriptionViewJpaRepository;
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
    private final KycCheckViewJpaRepository kycCheckViewRepository;
    private final AmlScreeningViewJpaRepository amlScreeningViewRepository;
    private final DocumentViewJpaRepository documentViewRepository;
    private final SubscriptionViewJpaRepository subscriptionViewRepository;

    public ReportingQueryService(FundNavViewJpaRepository fundNavViewRepository,
                                  PortfolioViewJpaRepository portfolioViewRepository,
                                  PositionViewJpaRepository positionViewRepository,
                                  PaymentTransferViewJpaRepository paymentTransferViewRepository,
                                  KycCheckViewJpaRepository kycCheckViewRepository,
                                  AmlScreeningViewJpaRepository amlScreeningViewRepository,
                                  DocumentViewJpaRepository documentViewRepository,
                                  SubscriptionViewJpaRepository subscriptionViewRepository) {
        this.fundNavViewRepository = fundNavViewRepository;
        this.portfolioViewRepository = portfolioViewRepository;
        this.positionViewRepository = positionViewRepository;
        this.paymentTransferViewRepository = paymentTransferViewRepository;
        this.kycCheckViewRepository = kycCheckViewRepository;
        this.amlScreeningViewRepository = amlScreeningViewRepository;
        this.documentViewRepository = documentViewRepository;
        this.subscriptionViewRepository = subscriptionViewRepository;
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

    @Transactional(readOnly = true)
    public Page<KycCheckView> listKycChecks(UUID customerId, Pageable pageable) {
        return customerId != null
                ? kycCheckViewRepository.findByCustomerId(customerId, pageable)
                : kycCheckViewRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Page<AmlScreeningView> listAmlScreenings(UUID customerId, Pageable pageable) {
        return customerId != null
                ? amlScreeningViewRepository.findByCustomerId(customerId, pageable)
                : amlScreeningViewRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Page<DocumentView> listDocuments(UUID customerId, Pageable pageable) {
        return customerId != null
                ? documentViewRepository.findByCustomerId(customerId, pageable)
                : documentViewRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Page<SubscriptionView> listSubscriptions(UUID customerId, Pageable pageable) {
        return customerId != null
                ? subscriptionViewRepository.findByCustomerId(customerId, pageable)
                : subscriptionViewRepository.findAll(pageable);
    }
}
