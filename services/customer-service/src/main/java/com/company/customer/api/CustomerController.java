package com.company.customer.api;

import com.company.customer.api.dto.CreateCustomerRequest;
import com.company.customer.api.dto.CustomerResponse;
import com.company.customer.api.dto.UpdateCustomerRequest;
import com.company.customer.api.mapper.CustomerMapper;
import com.company.customer.application.CustomerApplicationService;
import com.company.customer.domain.Customer;
import com.company.platform.web.response.ApiResponse;
import com.company.platform.web.response.PageMeta;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
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

    @GetMapping
    public ApiResponse<List<CustomerResponse>> search(
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 200), Sort.by("fullName").ascending());
        Page<Customer> result = customerApplicationService.search(query, pageable);

        List<CustomerResponse> data = result.getContent().stream().map(customerMapper::toResponse).toList();
        PageMeta meta = new PageMeta(result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
        return ApiResponse.of(data, meta);
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CustomerResponse>> create(@Valid @RequestBody CreateCustomerRequest request) {
        var customer = customerApplicationService.create(request.fullName(), request.email(), request.phone(),
                request.dateOfBirth(), request.partyType());
        var response = customerMapper.toResponse(customer);
        return ResponseEntity.created(URI.create("/api/v1/customers/" + response.id()))
                .body(ApiResponse.of(response));
    }

    @PutMapping("/{id}")
    public ApiResponse<CustomerResponse> update(@PathVariable UUID id, @Valid @RequestBody UpdateCustomerRequest request) {
        var customer = customerApplicationService.update(id, request.fullName(), request.phone(), request.dateOfBirth());
        return ApiResponse.of(customerMapper.toResponse(customer));
    }
}
