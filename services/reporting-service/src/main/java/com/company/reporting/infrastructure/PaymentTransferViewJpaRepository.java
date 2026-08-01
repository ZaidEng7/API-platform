package com.company.reporting.infrastructure;

import com.company.reporting.domain.PaymentTransferView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PaymentTransferViewJpaRepository extends JpaRepository<PaymentTransferView, UUID> {
    Page<PaymentTransferView> findByCustomerId(UUID customerId, Pageable pageable);
}
