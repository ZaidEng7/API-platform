package com.company.portfolio.application;

import com.company.platform.test.AbstractMessagingIntegrationTest;
import com.company.portfolio.domain.Position;
import com.company.portfolio.infrastructure.PositionJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves {@code recordPosition} is genuinely idempotent on
 * {@code sourceReference} — a debugging-focused review of the Investment
 * Service saga found that a retried call (e.g. the caller's own request
 * timed out after this endpoint had already succeeded) previously created
 * a second, duplicate position every time, since nothing here detected a
 * repeat call. Covers both the common case (a sequential retry, caught by
 * the up-front lookup) and the rare one (two calls racing each other,
 * caught only by {@link com.company.portfolio.infrastructure.PositionInsertGuard}'s
 * insert-conflict fallback) — against a real Postgres, since the fix
 * specifically depends on how Postgres handles a failed statement inside a
 * transaction.
 */
class PositionIdempotencyIntegrationTest extends AbstractMessagingIntegrationTest {

    @Autowired
    private PortfolioApplicationService portfolioApplicationService;

    @Autowired
    private PositionJpaRepository positionRepository;

    @Test
    void sequentialRetryWithTheSameSourceReferenceReturnsTheOriginalPosition() {
        UUID portfolioId = portfolioApplicationService.openPortfolio(UUID.randomUUID(), UUID.randomUUID(),
                "Test Portfolio", "USD").getId();
        String sourceReference = UUID.randomUUID().toString();

        Position first = portfolioApplicationService.recordPosition(portfolioId, "EQFND01", BigDecimal.TEN,
                sourceReference);
        Position second = portfolioApplicationService.recordPosition(portfolioId, "EQFND01", BigDecimal.TEN,
                sourceReference);

        assertThat(second.getId()).isEqualTo(first.getId());
        List<Position> all = positionRepository.findByPortfolioId(portfolioId);
        assertThat(all).hasSize(1);
    }

    @Test
    void concurrentCallsWithTheSameSourceReferenceCreateOnlyOnePosition() throws Exception {
        UUID portfolioId = portfolioApplicationService.openPortfolio(UUID.randomUUID(), UUID.randomUUID(),
                "Test Portfolio", "USD").getId();
        String sourceReference = UUID.randomUUID().toString();

        CyclicBarrier barrier = new CyclicBarrier(2);
        AtomicReference<Position> result1 = new AtomicReference<>();
        AtomicReference<Position> result2 = new AtomicReference<>();

        Thread t1 = new Thread(() -> {
            await(barrier);
            result1.set(portfolioApplicationService.recordPosition(portfolioId, "EQFND01", BigDecimal.TEN,
                    sourceReference));
        });
        Thread t2 = new Thread(() -> {
            await(barrier);
            result2.set(portfolioApplicationService.recordPosition(portfolioId, "EQFND01", BigDecimal.TEN,
                    sourceReference));
        });

        t1.start();
        t2.start();
        t1.join();
        t2.join();

        assertThat(result1.get().getId()).isEqualTo(result2.get().getId());
        List<Position> all = positionRepository.findByPortfolioId(portfolioId);
        assertThat(all).hasSize(1);
    }

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
