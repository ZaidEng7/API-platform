package com.company.crmadapter.legacy;

import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.company.platform.web.exception.ApiException;
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
 * real defaults.
 */
@SpringBootTest
class LegacyCrmClientResilienceTest {

    // Fixed, not dynamic: @DynamicPropertySource runs before this extension's
    // beforeAll() actually binds a dynamic port (SpringExtension, implied by
    // @SpringBootTest, registers/runs ahead of a @RegisterExtension static
    // field here), so reading wireMock.baseUrl() at that point is stale.
    private static final int WIREMOCK_PORT = 9998;

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(WireMockConfiguration.wireMockConfig().port(WIREMOCK_PORT))
            // Without this, the static WireMock.stubFor()/get() DSL methods
            // target the default client (localhost:8080), not this instance.
            .configureStaticDsl(true)
            .build();

    @DynamicPropertySource
    static void legacyCrmBaseUrl(DynamicPropertyRegistry registry) {
        registry.add("legacy-crm.base-url", () -> "http://localhost:" + WIREMOCK_PORT);
        registry.add("resilience4j.circuitbreaker.instances.legacyCrm.sliding-window-size", () -> 4);
        registry.add("resilience4j.circuitbreaker.instances.legacyCrm.minimum-number-of-calls", () -> 2);
        registry.add("resilience4j.retry.instances.legacyCrm.wait-duration", () -> "10ms");
    }

    @Autowired
    private LegacyCrmClient legacyCrmClient;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @BeforeEach
    void resetCircuitBreaker() {
        circuitBreakerRegistry.circuitBreaker("legacyCrm").reset();
    }

    @Test
    void retriesOnTransientFailureThenSucceeds() {
        wireMock.stubFor(get(urlPathMatching("/crm/v1/customers/.*"))
                .inScenario("transient-failure")
                .whenScenarioStateIs("Started")
                .willReturn(aResponse().withStatus(500))
                .willSetStateTo("second attempt"));
        wireMock.stubFor(get(urlPathMatching("/crm/v1/customers/.*"))
                .inScenario("transient-failure")
                .whenScenarioStateIs("second attempt")
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"CUST_ID": "42", "CUST_NM": "Ada Lovelace", "EMAIL_ADDR": "ada@example.com", "CUST_STATUS_CD": "A", "VIP_FLG": "Y"}
                                """)));

        var record = legacyCrmClient.getCustomer("42");

        assertThat(record.custId()).isEqualTo("42");
        wireMock.verify(2, getRequestedFor(urlPathMatching("/crm/v1/customers/.*")));
    }

    @Test
    void circuitBreakerOpensAfterRepeatedFailuresAndFallbackReturns503() {
        stubFor(get(urlPathMatching("/crm/v1/customers/.*")).willReturn(aResponse().withStatus(500)));

        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> legacyCrmClient.getCustomer("42")).isInstanceOf(ApiException.class);
        }

        assertThat(circuitBreakerRegistry.circuitBreaker("legacyCrm").getState())
                .isEqualTo(CircuitBreaker.State.OPEN);

        int requestsBeforeOpen = wireMock.getAllServeEvents().size();

        assertThatThrownBy(() -> legacyCrmClient.getCustomer("42"))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE);

        // Open circuit must short-circuit — no new request reaches WireMock.
        assertThat(wireMock.getAllServeEvents()).hasSize(requestsBeforeOpen);
    }
}
