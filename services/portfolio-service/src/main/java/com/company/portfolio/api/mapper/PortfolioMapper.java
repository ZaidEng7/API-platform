package com.company.portfolio.api.mapper;

import com.company.portfolio.api.dto.PortfolioResponse;
import com.company.portfolio.api.dto.PositionResponse;
import com.company.portfolio.domain.Portfolio;
import com.company.portfolio.domain.Position;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface PortfolioMapper {
    PortfolioResponse toResponse(Portfolio portfolio);

    PositionResponse toResponse(Position position);
}
