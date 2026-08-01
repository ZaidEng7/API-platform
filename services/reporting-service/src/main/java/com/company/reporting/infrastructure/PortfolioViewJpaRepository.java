package com.company.reporting.infrastructure;

import com.company.reporting.domain.PortfolioView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PortfolioViewJpaRepository extends JpaRepository<PortfolioView, UUID> {
    Page<PortfolioView> findByOwnerId(UUID ownerId, Pageable pageable);
}
