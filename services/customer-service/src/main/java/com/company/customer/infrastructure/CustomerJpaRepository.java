package com.company.customer.infrastructure;

import com.company.customer.domain.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CustomerJpaRepository extends JpaRepository<Customer, UUID> {

    Page<Customer> findByEmailContainingIgnoreCaseOrFullNameContainingIgnoreCase(
            String emailFragment, String fullNameFragment, Pageable pageable);
}
