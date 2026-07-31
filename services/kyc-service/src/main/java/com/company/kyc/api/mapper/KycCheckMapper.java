package com.company.kyc.api.mapper;

import com.company.kyc.api.dto.KycCheckResponse;
import com.company.kyc.domain.KycCheck;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface KycCheckMapper {
    KycCheckResponse toResponse(KycCheck kycCheck);
}
