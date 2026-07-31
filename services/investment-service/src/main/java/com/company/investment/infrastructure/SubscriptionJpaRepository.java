package com.company.investment.infrastructure;

import com.company.investment.domain.Subscription;
import com.company.investment.domain.SubscriptionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionJpaRepository extends JpaRepository<Subscription, UUID> {

    Optional<Subscription> findByIdempotencyKey(String idempotencyKey);

    Page<Subscription> findByOwnerId(UUID ownerId, Pageable pageable);

    List<Subscription> findByStatusAndTimeoutAtBefore(SubscriptionStatus status, Instant cutoff);
}
