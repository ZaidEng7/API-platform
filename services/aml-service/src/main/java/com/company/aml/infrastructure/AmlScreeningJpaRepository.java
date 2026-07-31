package com.company.aml.infrastructure;

import com.company.aml.domain.AmlScreening;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AmlScreeningJpaRepository extends JpaRepository<AmlScreening, UUID> {

    Page<AmlScreening> findByCustomerId(UUID customerId, Pageable pageable);
}
