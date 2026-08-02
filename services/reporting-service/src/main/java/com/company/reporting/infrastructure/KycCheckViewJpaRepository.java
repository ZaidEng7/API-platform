package com.company.reporting.infrastructure;

import com.company.reporting.domain.KycCheckView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface KycCheckViewJpaRepository extends JpaRepository<KycCheckView, UUID> {
    Page<KycCheckView> findByCustomerId(UUID customerId, Pageable pageable);
}
