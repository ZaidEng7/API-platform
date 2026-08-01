package com.company.reporting.infrastructure;

import com.company.reporting.domain.FundNavView;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FundNavViewJpaRepository extends JpaRepository<FundNavView, String> {
}
