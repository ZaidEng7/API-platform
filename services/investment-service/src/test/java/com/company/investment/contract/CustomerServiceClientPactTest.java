package com.company.investment.contract;

import au.com.dius.pact.consumer.MockServer;
import au.com.dius.pact.consumer.dsl.PactDslWithProvider;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.core.model.PactSpecVersion;
import au.com.dius.pact.core.model.RequestResponsePact;
import au.com.dius.pact.core.model.annotations.Pact;
import com.company.investment.infrastructure.client.CustomerServiceClient;
import com.company.platform.web.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real consumer-side half of the Investment Service ↔ Customer Service leg
 * of the subscription saga's "validate customer" step — Investment
 * Service's own {@link CustomerServiceClient}. Provider verification lives
 * in Customer Service's own {@code CustomerServicePactVerificationTest}
 * (which replaces the template pact that used to stand in for this before
 * a real consumer existed — see contracts/README.md history).
 */
@ExtendWith(PactConsumerTestExt.class)
class CustomerServiceClientPactTest {

    private static final UUID CUSTOMER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID UNKNOWN_CUSTOMER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Pact(consumer = "investment-service", provider = "customer-service")
    RequestResponsePact getCustomerById(PactDslWithProvider builder) {
        return builder
                .given("a customer with id 11111111-1111-1111-1111-111111111111 exists")
                .uponReceiving("a request for that customer")
                .path("/api/v1/customers/" + CUSTOMER_ID)
                .method("GET")
                .willRespondWith()
                .status(200)
                .headers(Map.of("Content-Type", "application/json"))
                .body("""
                        {
                          "success": true,
                          "data": {
                            "id": "%s",
                            "fullName": "Ada Lovelace",
                            "email": "ada@example.com",
                            "createdAt": "2026-01-01T00:00:00Z"
                          }
                        }
                        """.formatted(CUSTOMER_ID))
                .toPact();
    }

    @Pact(consumer = "investment-service", provider = "customer-service")
    RequestResponsePact getUnknownCustomerById(PactDslWithProvider builder) {
        return builder
                .given("no customer exists with id 22222222-2222-2222-2222-222222222222")
                .uponReceiving("a request for that customer")
                .path("/api/v1/customers/" + UNKNOWN_CUSTOMER_ID)
                .method("GET")
                .willRespondWith()
                .status(404)
                .headers(Map.of("Content-Type", "application/problem+json"))
                .body("""
                        {
                          "status": 404,
                          "errorCode": "CUST-4041"
                        }
                        """)
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "getCustomerById", pactVersion = PactSpecVersion.V3)
    void clientDoesNotThrowWhenCustomerExists(MockServer mockServer) {
        var client = new CustomerServiceClient(RestClient.builder().baseUrl(mockServer.getUrl()).build());

        assertThatCode(() -> client.requireExists(CUSTOMER_ID)).doesNotThrowAnyException();
    }

    @Test
    @PactTestFor(pactMethod = "getUnknownCustomerById", pactVersion = PactSpecVersion.V3)
    void clientThrowsWhenCustomerNotFound(MockServer mockServer) {
        var client = new CustomerServiceClient(RestClient.builder().baseUrl(mockServer.getUrl()).build());

        assertThatThrownBy(() -> client.requireExists(UNKNOWN_CUSTOMER_ID)).isInstanceOf(ApiException.class);
    }
}
