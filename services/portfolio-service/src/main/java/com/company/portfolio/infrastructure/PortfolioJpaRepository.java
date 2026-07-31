package com.company.portfolio.infrastructure;

import com.company.portfolio.domain.Portfolio;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PortfolioJpaRepository extends JpaRepository<Portfolio, UUID> {

    Page<Portfolio> findByOwnerId(UUID ownerId, Pageable pageable);
}
