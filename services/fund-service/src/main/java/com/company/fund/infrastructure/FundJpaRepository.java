package com.company.fund.infrastructure;

import com.company.fund.domain.Fund;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface FundJpaRepository extends JpaRepository<Fund, UUID> {

    Optional<Fund> findByFundCode(String fundCode);

    boolean existsByFundCode(String fundCode);
}
