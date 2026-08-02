package com.company.reporting.api.mapper;

import com.company.reporting.api.dto.AmlScreeningReportResponse;
import com.company.reporting.api.dto.DocumentReportResponse;
import com.company.reporting.api.dto.FundNavResponse;
import com.company.reporting.api.dto.KycCheckReportResponse;
import com.company.reporting.api.dto.PaymentTransferResponse;
import com.company.reporting.api.dto.PortfolioResponse;
import com.company.reporting.api.dto.PositionResponse;
import com.company.reporting.api.dto.SubscriptionReportResponse;
import com.company.reporting.domain.AmlScreeningView;
import com.company.reporting.domain.DocumentView;
import com.company.reporting.domain.FundNavView;
import com.company.reporting.domain.KycCheckView;
import com.company.reporting.domain.PaymentTransferView;
import com.company.reporting.domain.PortfolioView;
import com.company.reporting.domain.PositionView;
import com.company.reporting.domain.SubscriptionView;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ReportingMapper {
    FundNavResponse toResponse(FundNavView view);
    PortfolioResponse toResponse(PortfolioView view);
    PositionResponse toResponse(PositionView view);
    PaymentTransferResponse toResponse(PaymentTransferView view);
    KycCheckReportResponse toResponse(KycCheckView view);
    AmlScreeningReportResponse toResponse(AmlScreeningView view);
    DocumentReportResponse toResponse(DocumentView view);
    SubscriptionReportResponse toResponse(SubscriptionView view);
}
