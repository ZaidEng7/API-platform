package com.company.portfolio.infrastructure;

import com.company.portfolio.domain.Position;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Insert-first idempotency for {@code Position.sourceReference} (unique
 * where non-null, see {@code V2__position_source_reference.sql}): attempts
 * the insert in its own transaction, without poisoning the caller's own
 * transaction on conflict.
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
 * <p><strong>Deliberately does not catch the conflict itself</strong> —
 * catching {@code DataIntegrityViolationException} inside this method and
 * returning normally would make Spring's transaction interceptor try to
 * <em>commit</em> this transaction afterward, but the underlying Postgres
 * transaction is already aborted by the failed statement; attempting to
 * commit an aborted transaction throws its own (different, uncaught)
 * exception. Letting the conflict propagate out of this method instead
 * means Spring's interceptor rolls the nested transaction back correctly
 * (the standard behavior for an unchecked exception leaving a
 * {@code @Transactional} method) before re-throwing to the caller, whose
 * own transaction was only suspended by {@code REQUIRES_NEW}, never
 * touched, and is therefore still healthy for the fallback lookup.
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

    /** @throws org.springframework.dao.DataIntegrityViolationException if {@code position}'s sourceReference is already taken. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Position insert(Position position) {
        return positionRepository.saveAndFlush(position);
    }
}
