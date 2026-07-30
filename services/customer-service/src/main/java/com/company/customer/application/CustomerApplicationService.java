package com.company.customer.application;

import com.company.customer.domain.Customer;
import com.company.customer.infrastructure.CustomerJpaRepository;
import com.company.platform.web.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class CustomerApplicationService {

    private final CustomerJpaRepository customerRepository;

    public CustomerApplicationService(CustomerJpaRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @Transactional(readOnly = true)
    public Customer getById(UUID id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CUST-4041", "Customer not found: " + id));
    }

    @Transactional
    public Customer create(String fullName, String email) {
        Customer customer = new Customer(UUID.randomUUID(), fullName, email, Instant.now());
        return customerRepository.save(customer);
    }
}
