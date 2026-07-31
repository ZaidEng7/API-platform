package com.company.fund.api.mapper;

import com.company.fund.api.dto.FundResponse;
import com.company.fund.api.dto.NavSnapshotResponse;
import com.company.fund.domain.Fund;
import com.company.fund.domain.NavSnapshot;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface FundMapper {
    FundResponse toResponse(Fund fund);

    NavSnapshotResponse toResponse(NavSnapshot navSnapshot);
}
