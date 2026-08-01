package com.company.investment.contract;

import au.com.dius.pact.consumer.MockServer;
import au.com.dius.pact.consumer.dsl.PactDslWithProvider;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.core.model.PactSpecVersion;
import au.com.dius.pact.core.model.RequestResponsePact;
import au.com.dius.pact.core.model.annotations.Pact;
import com.company.investment.infrastructure.client.KycCheckClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real consumer-side half of the Investment Service ↔ KYC Service leg of
 * the subscription saga's "KYC/AML check" step. Only one interaction: the
 * client's {@code isApproved} only branches on response *content*
 * (empty/PENDING/REJECTED all fold to {@code false}), not response status
 * or shape, so one representative 200 covers everything this consumer
 * actually depends on structurally.
 */
@ExtendWith(PactConsumerTestExt.class)
class KycCheckClientPactTest {

    private static final UUID CUSTOMER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Pact(consumer = "investment-service", provider = "kyc-service")
    RequestResponsePact getMostRecentKycCheck(PactDslWithProvider builder) {
        return builder
                .given("customer 11111111-1111-1111-1111-111111111111 has an approved KYC check")
                .uponReceiving("a request for that customer's most recent KYC check")
                .path("/api/v1/kyc-checks")
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
                              "id": "33333333-3333-3333-3333-333333333333",
                              "customerId": "%s",
                              "status": "APPROVED"
                            }
                          ],
                          "meta": {"page": 0, "size": 1, "totalElements": 1, "totalPages": 1}
                        }
                        """.formatted(CUSTOMER_ID))
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "getMostRecentKycCheck", pactVersion = PactSpecVersion.V3)
    void clientReportsApproved(MockServer mockServer) {
        var client = new KycCheckClient(RestClient.builder().baseUrl(mockServer.getUrl()).build());

        assertThat(client.isApproved(CUSTOMER_ID)).isTrue();
    }
}
