package com.company.investment.api;

import com.company.platform.test.AbstractMessagingIntegrationTest;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves {@code SubscriptionTimeoutJob} is real, not just present — guide
 * §8.4: "every saga has a timeout and a dead-letter path". The timeout and
 * check interval are both shrunk to near-instant for this test class only,
 * so this proves the scheduled job actually flips a stuck subscription to
 * TIMED_OUT, not just that the code compiles.
 */
@WithMockUser(roles = "OPERATIONS")
class SubscriptionTimeoutJobIntegrationTest extends AbstractMessagingIntegrationTest {

    private static final int WIREMOCK_PORT = 9991;

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(WireMockConfiguration.wireMockConfig().port(WIREMOCK_PORT))
            .configureStaticDsl(true)
            .build();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        String base = "http://localhost:" + WIREMOCK_PORT;
        registry.add("customer-service.base-url", () -> base);
        registry.add("kyc-service.base-url", () -> base);
        registry.add("aml-service.base-url", () -> base);
        registry.add("portfolio-service.base-url", () -> base);
        registry.add("investment.subscription.timeout", () -> "PT1S");
        registry.add("investment.subscription.timeout-check-interval-ms", () -> "500");
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void aSubscriptionLeftAwaitingPaymentPastItsTimeoutIsMarkedTimedOut() throws Exception {
        UUID customerId = UUID.randomUUID();
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

        String location = mockMvc.perform(post("/api/v1/subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .content("""
                                {"customerId": "%s", "ownerId": "%s", "portfolioId": "%s", "fundCode": "EQFND01", "quantity": 100}
                                """.formatted(customerId, UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("AWAITING_PAYMENT"))
                .andReturn().getResponse().getHeader("Location");

        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() ->
                mockMvc.perform(get(location))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.status").value("TIMED_OUT")));
    }
}
