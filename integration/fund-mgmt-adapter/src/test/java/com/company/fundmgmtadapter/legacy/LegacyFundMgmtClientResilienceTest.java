package com.company.fundmgmtadapter.legacy;

import com.company.platform.web.exception.ApiException;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves the §9.4 resilience config actually does something, against a real
 * (if fake) HTTP server — not just that the annotations are present.
 * Overrides the sliding window down to a size WireMock can drive in a
 * handful of calls; production config (application.yml) uses the guide's
 * real defaults. Talks to {@link LegacyFundMgmtClient} directly, bypassing
 * {@link FundNavProvider}'s cache — this is about the resilient-fetch path,
 * not the cache (see FundNavProviderCachingTest for that).
 */
@SpringBootTest
class LegacyFundMgmtClientResilienceTest {

    // Fixed, not dynamic — see FundNavProviderCachingTest for why.
    private static final int WIREMOCK_PORT = 9992;

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(WireMockConfiguration.wireMockConfig().port(WIREMOCK_PORT))
            .configureStaticDsl(true)
            .build();

    @DynamicPropertySource
    static void legacyFundMgmtBaseUrl(DynamicPropertyRegistry registry) {
        registry.add("legacy-fund-mgmt.base-url", () -> "http://localhost:" + WIREMOCK_PORT);
        registry.add("resilience4j.circuitbreaker.instances.legacyFundMgmt.sliding-window-size", () -> 4);
        registry.add("resilience4j.circuitbreaker.instances.legacyFundMgmt.minimum-number-of-calls", () -> 2);
        registry.add("resilience4j.retry.instances.legacyFundMgmt.wait-duration", () -> "10ms");
    }

    @Autowired
    private LegacyFundMgmtClient legacyFundMgmtClient;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @BeforeEach
    void resetCircuitBreaker() {
        circuitBreakerRegistry.circuitBreaker("legacyFundMgmt").reset();
    }

    @Test
    void retriesOnTransientFailureThenSucceeds() {
        wireMock.stubFor(get(urlPathMatching("/fundmgmt/v1/funds/.*/nav"))
                .inScenario("transient-failure")
                .whenScenarioStateIs("Started")
                .willReturn(aResponse().withStatus(500))
                .willSetStateTo("second attempt"));
        wireMock.stubFor(get(urlPathMatching("/fundmgmt/v1/funds/.*/nav"))
                .inScenario("transient-failure")
                .whenScenarioStateIs("second attempt")
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"FUND_CD": "EQFND01", "NAV_VALUE_X10000": 105023, "NAV_DT_YYYYMMDD": "20260730"}
                                """)));

        var record = legacyFundMgmtClient.getNav("EQFND01");

        assertThat(record.fundCd()).isEqualTo("EQFND01");
        wireMock.verify(2, getRequestedFor(urlPathMatching("/fundmgmt/v1/funds/.*/nav")));
    }

    @Test
    void circuitBreakerOpensAfterRepeatedFailuresAndFallbackReturns503() {
        stubFor(get(urlPathMatching("/fundmgmt/v1/funds/.*/nav")).willReturn(aResponse().withStatus(500)));

        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> legacyFundMgmtClient.getNav("EQFND01")).isInstanceOf(ApiException.class);
        }

        assertThat(circuitBreakerRegistry.circuitBreaker("legacyFundMgmt").getState())
                .isEqualTo(CircuitBreaker.State.OPEN);

        int requestsBeforeOpen = wireMock.getAllServeEvents().size();

        assertThatThrownBy(() -> legacyFundMgmtClient.getNav("EQFND01"))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE);

        // Open circuit must short-circuit — no new request reaches WireMock.
        assertThat(wireMock.getAllServeEvents()).hasSize(requestsBeforeOpen);
    }
}
