package com.company.investment.application;

import com.company.investment.domain.Subscription;
import com.company.investment.domain.SubscriptionStatus;
import com.company.investment.infrastructure.SubscriptionJpaRepository;
import com.company.investment.infrastructure.scheduling.SubscriptionTimeoutProcessor;
import com.company.platform.test.AbstractMessagingIntegrationTest;
import com.company.platform.web.exception.ApiException;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicReference;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the two race conditions found in a debugging-focused review of the
 * subscription saga are actually fixed against a real Postgres — a naive
 * check-then-act idempotency check and a lack of optimistic locking would
 * both pass a sequential test while still being broken under genuine
 * concurrency, so these tests use real threads plus a {@link CyclicBarrier}
 * to force both sides of each race to actually contend for the same row.
 */
class SubscriptionConcurrencyIntegrationTest extends AbstractMessagingIntegrationTest {

    private static final int WIREMOCK_PORT = 9992;

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(WireMockConfiguration.wireMockConfig().port(WIREMOCK_PORT))
            .configureStaticDsl(true)
            .build();

    @DynamicPropertySource
    static void externalServiceUrls(DynamicPropertyRegistry registry) {
        String base = "http://localhost:" + WIREMOCK_PORT;
        registry.add("customer-service.base-url", () -> base);
        registry.add("kyc-service.base-url", () -> base);
        registry.add("aml-service.base-url", () -> base);
        registry.add("portfolio-service.base-url", () -> base);
    }

    @Autowired
    private SubscriptionApplicationService subscriptionApplicationService;

    @Autowired
    private SubscriptionTimeoutProcessor subscriptionTimeoutProcessor;

    @Autowired
    private SubscriptionJpaRepository subscriptionRepository;

    @BeforeEach
    void resetStubs() {
        wireMock.resetAll();
    }

    @Test
    void concurrentDuplicateIdempotencyKeySubmissionsReturnTheSameSubscription() throws Exception {
        UUID customerId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID portfolioId = UUID.randomUUID();
        String idempotencyKey = UUID.randomUUID().toString();
        stubApprovedCustomer(customerId);

        CyclicBarrier barrier = new CyclicBarrier(2);
        AtomicReference<Subscription> result1 = new AtomicReference<>();
        AtomicReference<Subscription> result2 = new AtomicReference<>();

        Thread t1 = new Thread(() -> {
            await(barrier);
            result1.set(subscriptionApplicationService.requestSubscription(idempotencyKey, customerId, ownerId,
                    portfolioId, "EQFND01", BigDecimal.TEN));
        });
        Thread t2 = new Thread(() -> {
            await(barrier);
            result2.set(subscriptionApplicationService.requestSubscription(idempotencyKey, customerId, ownerId,
                    portfolioId, "EQFND01", BigDecimal.TEN));
        });

        t1.start();
        t2.start();
        t1.join();
        t2.join();

        assertThat(result1.get()).isNotNull();
        assertThat(result2.get()).isNotNull();
        assertThat(result1.get().getId()).isEqualTo(result2.get().getId());
        assertThat(subscriptionRepository.findByIdempotencyKey(idempotencyKey)).isPresent();
    }

    @Test
    void confirmAndTimeoutRaceResolvesToExactlyOneWinnerNeverBoth() throws Exception {
        UUID id = UUID.randomUUID();
        UUID portfolioId = UUID.randomUUID();
        Instant now = Instant.now();
        // Already past its timeout, so SubscriptionTimeoutProcessor considers it eligible immediately.
        Subscription subscription = Subscription.reserved(id, UUID.randomUUID().toString(), UUID.randomUUID(),
                UUID.randomUUID(), portfolioId, "EQFND01", BigDecimal.TEN, now.minusSeconds(120),
                now.minusSeconds(1));
        subscriptionRepository.saveAndFlush(subscription);

        stubFor(post(urlPathMatching("/api/v1/portfolios/" + portfolioId + "/positions"))
                .willReturn(aResponse().withStatus(201).withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"success": true, "data": {"id": "%s"}}
                                """.formatted(UUID.randomUUID()))));

        CyclicBarrier barrier = new CyclicBarrier(2);
        AtomicReference<Object> confirmOutcome = new AtomicReference<>();
        AtomicReference<Boolean> timeoutOutcome = new AtomicReference<>();

        Thread confirmThread = new Thread(() -> {
            await(barrier);
            try {
                subscriptionApplicationService.confirmPayment(id);
                confirmOutcome.set("CONFIRMED");
            } catch (ApiException e) {
                confirmOutcome.set(e);
            }
        });
        Thread timeoutThread = new Thread(() -> {
            await(barrier);
            timeoutOutcome.set(subscriptionTimeoutProcessor.tryTimeOut(id));
        });

        confirmThread.start();
        timeoutThread.start();
        confirmThread.join();
        timeoutThread.join();

        Subscription finalState = subscriptionRepository.findById(id).orElseThrow();

        if ("CONFIRMED".equals(confirmOutcome.get())) {
            // Confirm won: the position was recorded, the row is CONFIRMED, and
            // the timeout attempt must have cleanly lost (not overwritten it).
            assertThat(finalState.getStatus()).isEqualTo(SubscriptionStatus.CONFIRMED);
            assertThat(timeoutOutcome.get()).isFalse();
            wireMock.verify(1, WireMock.postRequestedFor(urlPathMatching("/api/v1/portfolios/.*/positions")));
        } else {
            // Timeout won: confirm must have been cleanly rejected (409, not a
            // silent no-op and not a position recorded anyway), and the row is
            // TIMED_OUT.
            assertThat(confirmOutcome.get()).isInstanceOf(ApiException.class);
            assertThat(((ApiException) confirmOutcome.get()).getStatus()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(finalState.getStatus()).isEqualTo(SubscriptionStatus.TIMED_OUT);
            assertThat(timeoutOutcome.get()).isTrue();
            wireMock.verify(0, WireMock.postRequestedFor(urlPathMatching("/api/v1/portfolios/.*/positions")));
        }
    }

    private void stubApprovedCustomer(UUID customerId) {
        stubFor(WireMock.get(urlPathMatching("/api/v1/customers/" + customerId))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"success": true, "data": {"id": "%s"}}
                                """.formatted(customerId))));
        stubFor(WireMock.get(urlPathMatching("/api/v1/kyc-checks.*"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"success": true, "data": [{"status": "APPROVED"}]}
                                """)));
        stubFor(WireMock.get(urlPathMatching("/api/v1/aml/screenings.*"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"success": true, "data": [{"status": "COMPLETED", "outcome": "CLEAR"}]}
                                """)));
    }

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
