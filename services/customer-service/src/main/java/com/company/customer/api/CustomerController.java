package com.company.customer.api;

import com.company.customer.api.dto.CreateCustomerRequest;
import com.company.customer.api.dto.CustomerResponse;
import com.company.customer.api.mapper.CustomerMapper;
import com.company.customer.application.CustomerApplicationService;
import com.company.platform.web.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/customers")
public class CustomerController {

    private final CustomerApplicationService customerApplicationService;
    private final CustomerMapper customerMapper;

    public CustomerController(CustomerApplicationService customerApplicationService, CustomerMapper customerMapper) {
        this.customerApplicationService = customerApplicationService;
        this.customerMapper = customerMapper;
    }

    @GetMapping("/{id}")
    public ApiResponse<CustomerResponse> getById(@PathVariable UUID id) {
        return ApiResponse.of(customerMapper.toResponse(customerApplicationService.getById(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CustomerResponse>> create(@Valid @RequestBody CreateCustomerRequest request) {
        var customer = customerApplicationService.create(request.fullName(), request.email());
        var response = customerMapper.toResponse(customer);
        return ResponseEntity.created(URI.create("/api/v1/customers/" + response.id()))
                .body(ApiResponse.of(response));
    }
}
