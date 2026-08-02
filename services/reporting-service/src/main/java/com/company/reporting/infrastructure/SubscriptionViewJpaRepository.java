package com.company.reporting.infrastructure;

import com.company.reporting.domain.SubscriptionView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SubscriptionViewJpaRepository extends JpaRepository<SubscriptionView, UUID> {
    Page<SubscriptionView> findByCustomerId(UUID customerId, Pageable pageable);
}
