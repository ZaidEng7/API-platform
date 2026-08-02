package com.company.portfolio.infrastructure;

import com.company.portfolio.domain.Position;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Insert-first idempotency for {@code Position.sourceReference} (unique
 * where non-null, see {@code V2__position_source_reference.sql}): attempts
 * the insert and reports whether this call won the race, without poisoning
 * the caller's own transaction on conflict.
 *
 * <p>Runs in its own {@code REQUIRES_NEW} transaction deliberately — a
 * unique-constraint violation aborts the whole transaction at the Postgres
 * level (not just the one failed statement), so catching the exception in
 * the *same* transaction as the caller would leave that transaction unusable
 * for the fallback lookup {@link com.company.portfolio.application.PortfolioApplicationService#recordPosition}
 * needs to do afterward. Isolating the attempt in its own transaction means
 * only this insert rolls back on conflict; the caller's transaction is
 * untouched and can safely query for the existing row.
 *
 * <p>A separate bean (not a method on {@code PortfolioApplicationService}
 * itself) because Spring's proxy-based transaction AOP doesn't intercept
 * same-class self-invocation — the same reason {@code fund-mgmt-adapter}
 * splits its caching and resilience annotations across two beans.
 */
@Component
public class PositionInsertGuard {

    private final PositionJpaRepository positionRepository;

    public PositionInsertGuard(PositionJpaRepository positionRepository) {
        this.positionRepository = positionRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<Position> tryInsert(Position position) {
        try {
            return Optional.of(positionRepository.saveAndFlush(position));
        } catch (DataIntegrityViolationException e) {
            return Optional.empty();
        }
    }
}
