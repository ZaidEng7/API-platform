package com.company.reporting.api.mapper;

import com.company.reporting.api.dto.FundNavResponse;
import com.company.reporting.api.dto.PaymentTransferResponse;
import com.company.reporting.api.dto.PortfolioResponse;
import com.company.reporting.api.dto.PositionResponse;
import com.company.reporting.domain.FundNavView;
import com.company.reporting.domain.PaymentTransferView;
import com.company.reporting.domain.PortfolioView;
import com.company.reporting.domain.PositionView;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ReportingMapper {
    FundNavResponse toResponse(FundNavView view);
    PortfolioResponse toResponse(PortfolioView view);
    PositionResponse toResponse(PositionView view);
    PaymentTransferResponse toResponse(PaymentTransferView view);
}
