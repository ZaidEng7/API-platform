package com.company.kyc.infrastructure;

import com.company.kyc.domain.KycCheck;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface KycCheckJpaRepository extends JpaRepository<KycCheck, UUID> {

    Page<KycCheck> findByCustomerId(UUID customerId, Pageable pageable);
}
