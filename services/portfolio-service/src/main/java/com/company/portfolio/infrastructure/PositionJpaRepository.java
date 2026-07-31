package com.company.portfolio.infrastructure;

import com.company.portfolio.domain.Position;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PositionJpaRepository extends JpaRepository<Position, UUID> {

    Page<Position> findByPortfolioId(UUID portfolioId, Pageable pageable);

    List<Position> findByPortfolioId(UUID portfolioId);
}
