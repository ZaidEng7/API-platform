package com.company.contracts.customerconsumer;

import au.com.dius.pact.consumer.MockServer;
import au.com.dius.pact.consumer.dsl.PactDslWithProvider;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.core.model.PactSpecVersion;
import au.com.dius.pact.core.model.RequestResponsePact;
import au.com.dius.pact.core.model.annotations.Pact;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * TEMPLATE contract test. Proves the Pact toolchain end to end (consumer DSL
 * here, provider verification in
 * services/customer-service's CustomerServicePactVerificationTest, both
 * publishing to/reading from the broker in
 * deployment/docker/pact-broker.yml) — it deliberately checks response
 * *shape* only, not real business rules. Replace this pair with your own
 * once a real consumer of customer-service exists; see ../../../README.md.
 */
@ExtendWith(PactConsumerTestExt.class)
class CustomerServiceClientPactTest {

    private static final UUID CUSTOMER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Pact(consumer = "gateway-example-consumer", provider = "customer-service")
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

    @Test
    @PactTestFor(pactMethod = "getCustomerById", pactVersion = PactSpecVersion.V3)
    void clientParsesCustomerResponse(MockServer mockServer) throws Exception {
        var client = new CustomerServiceClient(mockServer.getUrl());

        var customer = client.getById(CUSTOMER_ID);

        assertEquals(CUSTOMER_ID, customer.id());
        assertEquals("Ada Lovelace", customer.fullName());
        assertEquals("ada@example.com", customer.email());
    }
}
