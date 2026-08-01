package com.company.reporting.application;

import com.company.reporting.domain.PortfolioView;
import com.company.reporting.domain.PositionView;

import java.util.List;

public record PortfolioDetail(PortfolioView portfolio, List<PositionView> positions) {
}
