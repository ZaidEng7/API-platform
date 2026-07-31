package com.company.onboardingadapter.legacy;

import com.company.onboardingadapter.legacy.dto.LegacyOnboardingApplicationRequest;
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
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves the §9.4/§9.3 resilience config actually does something, against a
 * real (if fake) HTTP server — not just that the annotations are present.
 * Overrides the sliding window down to a size WireMock can drive in a
 * handful of calls; production config (application.yml) uses the guide's
 * real defaults.
 */
@SpringBootTest
class LegacyOnboardingClientResilienceTest {

    // Fixed, not dynamic: @DynamicPropertySource runs before this
    // extension's beforeAll() actually binds a dynamic port
    // (SpringExtension, implied by @SpringBootTest, registers/runs ahead of
    // a @RegisterExtension static field here), so reading wireMock.baseUrl()
    // at that point is stale.
    private static final int WIREMOCK_PORT = 9996;

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(WireMockConfiguration.wireMockConfig().port(WIREMOCK_PORT))
            // Without this, the static WireMock.stubFor()/post() DSL methods
            // target the default client (localhost:8080), not this instance.
            .configureStaticDsl(true)
            .build();

    @DynamicPropertySource
    static void legacyOnboardingBaseUrl(DynamicPropertyRegistry registry) {
        registry.add("legacy-onboarding.base-url", () -> "http://localhost:" + WIREMOCK_PORT);
        registry.add("resilience4j.circuitbreaker.instances.legacyOnboarding.sliding-window-size", () -> 4);
        registry.add("resilience4j.circuitbreaker.instances.legacyOnboarding.minimum-number-of-calls", () -> 2);
    }

    @Autowired
    private LegacyOnboardingClient legacyOnboardingClient;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @BeforeEach
    void resetCircuitBreaker() {
        circuitBreakerRegistry.circuitBreaker("legacyOnboarding").reset();
    }

    private static final LegacyOnboardingApplicationRequest REQUEST =
            new LegacyOnboardingApplicationRequest("Ada Lovelace", "ada@example.com", "19900305");

    @Test
    void doesNotRetryAFailedWrite() {
        // Deliberately no scenario/state-transition stub, unlike the CRM
        // adapter's retry test — every request gets a 500. If @Retry were
        // (wrongly) applied here, this would see multiple requests for one
        // client call.
        stubFor(post(urlPathMatching("/onboarding/v1/applications")).willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> legacyOnboardingClient.submit(REQUEST)).isInstanceOf(ApiException.class);

        wireMock.verify(1, postRequestedFor(urlPathMatching("/onboarding/v1/applications")));
    }

    @Test
    void circuitBreakerOpensAfterRepeatedFailuresAndFallbackReturns503() {
        stubFor(post(urlPathMatching("/onboarding/v1/applications")).willReturn(aResponse().withStatus(500)));

        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> legacyOnboardingClient.submit(REQUEST)).isInstanceOf(ApiException.class);
        }

        assertThat(circuitBreakerRegistry.circuitBreaker("legacyOnboarding").getState())
                .isEqualTo(CircuitBreaker.State.OPEN);

        int requestsBeforeOpen = wireMock.getAllServeEvents().size();

        assertThatThrownBy(() -> legacyOnboardingClient.submit(REQUEST))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE);

        // Open circuit must short-circuit — no new request reaches WireMock.
        assertThat(wireMock.getAllServeEvents()).hasSize(requestsBeforeOpen);
    }
}
