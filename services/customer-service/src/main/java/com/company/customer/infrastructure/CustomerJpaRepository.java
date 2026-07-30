package com.company.customer.infrastructure;

import com.company.customer.domain.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CustomerJpaRepository extends JpaRepository<Customer, UUID> {
}
