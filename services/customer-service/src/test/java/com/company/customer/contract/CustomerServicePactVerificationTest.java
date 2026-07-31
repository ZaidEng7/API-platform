package com.company.customer.contract;

import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junit5.PactVerificationInvocationContextProvider;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.State;
import au.com.dius.pact.provider.junitsupport.loader.PactBroker;
import au.com.dius.pact.provider.spring.junit5.MockMvcTestTarget;
import com.company.customer.application.CustomerApplicationService;
import com.company.customer.domain.Customer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.when;

/**
 * TEMPLATE provider verification — the customer-service half of the Pact
 * pair started by CustomerServiceClientPactTest in
 * contracts/customer-consumer-example. Verifies the real controller against
 * whatever pacts have been published to the broker
 * (deployment/docker/pact-broker.yml); the application service is stubbed
 * so this doesn't need a live database, matching this example's
 * shape-only scope. Excluded from the default `mvn verify` run (see the
 * "pact" excludedGroup in this module's surefire config) — it needs the
 * broker up and the consumer pact already published, which only the
 * dedicated "pact-contract-verification" CI job guarantees. Replace once a
 * real consumer/provider pair exists; see contracts/README.md.
 */
@Tag("pact")
@Provider("customer-service")
@PactBroker(url = "${PACT_BROKER_URL:http://localhost:9292}")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class CustomerServicePactVerificationTest {

    private static final UUID CUSTOMER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CustomerApplicationService customerApplicationService;

    @BeforeEach
    void setUpTestTarget(PactVerificationContext context) {
        context.setTarget(new MockMvcTestTarget(mockMvc));
    }

    @State("a customer with id 11111111-1111-1111-1111-111111111111 exists")
    void customerExists() {
        when(customerApplicationService.getById(CUSTOMER_ID)).thenReturn(
                new Customer(CUSTOMER_ID, "Ada Lovelace", "ada@example.com", Instant.parse("2026-01-01T00:00:00Z")));
    }

    @TestTemplate
    @ExtendWith(PactVerificationInvocationContextProvider.class)
    void pactVerificationTestTemplate(PactVerificationContext context) {
        context.verifyInteraction();
    }
}
