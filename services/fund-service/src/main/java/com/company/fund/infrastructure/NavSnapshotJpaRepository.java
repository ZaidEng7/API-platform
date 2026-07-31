package com.company.fund.infrastructure;

import com.company.fund.domain.NavSnapshot;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface NavSnapshotJpaRepository extends JpaRepository<NavSnapshot, UUID> {

    Page<NavSnapshot> findByFundCode(String fundCode, Pageable pageable);

    Optional<NavSnapshot> findFirstByFundCodeOrderByFetchedAtDesc(String fundCode);
}
