package com.company.payment.api.mapper;

import com.company.payment.api.dto.TransferResponse;
import com.company.payment.domain.Transfer;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface TransferMapper {
    TransferResponse toResponse(Transfer transfer);
}
