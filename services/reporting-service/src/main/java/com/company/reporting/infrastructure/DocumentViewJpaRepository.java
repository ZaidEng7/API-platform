package com.company.reporting.infrastructure;

import com.company.reporting.domain.DocumentView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DocumentViewJpaRepository extends JpaRepository<DocumentView, UUID> {
    Page<DocumentView> findByCustomerId(UUID customerId, Pageable pageable);
}
