package com.company.reporting.infrastructure;

import com.company.reporting.domain.AmlScreeningView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AmlScreeningViewJpaRepository extends JpaRepository<AmlScreeningView, UUID> {
    Page<AmlScreeningView> findByCustomerId(UUID customerId, Pageable pageable);
}
