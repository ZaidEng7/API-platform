package com.company.aml.api.mapper;

import com.company.aml.api.dto.ScreeningResponse;
import com.company.aml.domain.AmlScreening;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ScreeningMapper {
    ScreeningResponse toResponse(AmlScreening screening);
}
