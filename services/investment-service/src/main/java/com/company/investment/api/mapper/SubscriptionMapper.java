package com.company.investment.api.mapper;

import com.company.investment.api.dto.SubscriptionResponse;
import com.company.investment.domain.Subscription;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface SubscriptionMapper {
    SubscriptionResponse toResponse(Subscription subscription);
}
