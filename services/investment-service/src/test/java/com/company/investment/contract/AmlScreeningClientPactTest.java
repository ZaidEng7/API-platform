package com.company.investment.contract;

import au.com.dius.pact.consumer.MockServer;
import au.com.dius.pact.consumer.dsl.PactDslWithProvider;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.core.model.PactSpecVersion;
import au.com.dius.pact.core.model.RequestResponsePact;
import au.com.dius.pact.core.model.annotations.Pact;
import com.company.investment.infrastructure.client.AmlScreeningClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real consumer-side half of the Investment Service ↔ AML Service leg of
 * the subscription saga's "KYC/AML check" step — same one-interaction
 * rationale as {@link KycCheckClientPactTest}: the client only branches on
 * response content, not shape.
 */
@ExtendWith(PactConsumerTestExt.class)
class AmlScreeningClientPactTest {

    private static final UUID CUSTOMER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Pact(consumer = "investment-service", provider = "aml-service")
    RequestResponsePact getMostRecentScreening(PactDslWithProvider builder) {
        return builder
                .given("customer 11111111-1111-1111-1111-111111111111 has a clear AML screening")
                .uponReceiving("a request for that customer's most recent AML screening")
                .path("/api/v1/aml/screenings")
                .query("customerId=" + CUSTOMER_ID + "&page=0&size=1")
                .method("GET")
                .willRespondWith()
                .status(200)
                .headers(Map.of("Content-Type", "application/json"))
                .body("""
                        {
                          "success": true,
                          "data": [
                            {
                              "id": "44444444-4444-4444-4444-444444444444",
                              "customerId": "%s",
                              "status": "COMPLETED",
                              "outcome": "CLEAR"
                            }
                          ],
                          "meta": {"page": 0, "size": 1, "totalElements": 1, "totalPages": 1}
                        }
                        """.formatted(CUSTOMER_ID))
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "getMostRecentScreening", pactVersion = PactSpecVersion.V3)
    void clientReportsClear(MockServer mockServer) {
        var client = new AmlScreeningClient(RestClient.builder().baseUrl(mockServer.getUrl()).build());

        assertThat(client.isClear(CUSTOMER_ID)).isTrue();
    }
}
