package com.company.investment.contract;

import au.com.dius.pact.consumer.MockServer;
import au.com.dius.pact.consumer.dsl.PactDslWithProvider;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.core.model.PactSpecVersion;
import au.com.dius.pact.core.model.RequestResponsePact;
import au.com.dius.pact.core.model.annotations.Pact;
import com.company.investment.infrastructure.client.PortfolioPositionClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Real consumer-side half of the Investment Service ↔ Portfolio Service
 * leg of the subscription saga's "confirm" step — Investment Service's own
 * {@link PortfolioPositionClient}, which materializes the actual position
 * once payment is confirmed. The client discards the response body
 * ({@code toBodilessEntity()}), so only the request shape and status code
 * matter here.
 */
@ExtendWith(PactConsumerTestExt.class)
class PortfolioPositionClientPactTest {

    private static final UUID PORTFOLIO_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID SUBSCRIPTION_ID = UUID.fromString("77777777-7777-7777-7777-777777777777");

    @Pact(consumer = "investment-service", provider = "portfolio-service")
    RequestResponsePact recordPosition(PactDslWithProvider builder) {
        return builder
                .given("portfolio 55555555-5555-5555-5555-555555555555 exists")
                .uponReceiving("a request to record a fund position")
                .path("/api/v1/portfolios/" + PORTFOLIO_ID + "/positions")
                .method("POST")
                .headers(Map.of("Content-Type", "application/json"))
                .body("""
                        {"fundCode": "EQFND01", "quantity": 100, "sourceReference": "%s"}
                        """.formatted(SUBSCRIPTION_ID))
                .willRespondWith()
                .status(201)
                .headers(Map.of("Content-Type", "application/json"))
                .body("""
                        {
                          "success": true,
                          "data": {
                            "id": "66666666-6666-6666-6666-666666666666",
                            "portfolioId": "%s",
                            "fundCode": "EQFND01",
                            "quantity": 100,
                            "createdAt": "2026-08-01T00:00:00Z"
                          }
                        }
                        """.formatted(PORTFOLIO_ID))
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "recordPosition", pactVersion = PactSpecVersion.V3)
    void clientDoesNotThrowOnSuccess(MockServer mockServer) {
        var client = new PortfolioPositionClient(RestClient.builder().baseUrl(mockServer.getUrl()).build());

        assertThatCode(() -> client.recordPosition(PORTFOLIO_ID, "EQFND01", new BigDecimal("100"), SUBSCRIPTION_ID))
                .doesNotThrowAnyException();
    }
}
