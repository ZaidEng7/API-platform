package com.company.customer.api.mapper;

import com.company.customer.api.dto.CustomerResponse;
import com.company.customer.domain.Customer;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface CustomerMapper {
    CustomerResponse toResponse(Customer customer);
}
