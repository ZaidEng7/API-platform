package com.company.customer.contract;

import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.State;
import au.com.dius.pact.provider.junitsupport.loader.PactBroker;
import au.com.dius.pact.provider.spring.spring7.PactVerificationSpring7Provider;
import au.com.dius.pact.provider.spring.spring7.Spring7MockMvcTestTarget;
import com.company.customer.application.CustomerApplicationService;
import com.company.customer.domain.Customer;
import com.company.platform.web.exception.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        // Mocking CustomerApplicationService only removes the *repository*
        // call — Spring Boot still autoconfigures a real DataSource/JPA/
        // Flyway at context startup unless told not to, which would need a
        // live Postgres this test has no business requiring.
        properties = {
                "spring.autoconfigure.exclude="
                        + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
                        + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                        + "org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration,"
                        + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration"
        })
@AutoConfigureMockMvc
class CustomerServicePactVerificationTest {

    private static final UUID CUSTOMER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID UNKNOWN_CUSTOMER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CustomerApplicationService customerApplicationService;

    @BeforeEach
    void setUpTestTarget(PactVerificationContext context) {
        context.setTarget(new Spring7MockMvcTestTarget(mockMvc));
    }

    @State("a customer with id 11111111-1111-1111-1111-111111111111 exists")
    void customerExists() {
        when(customerApplicationService.getById(CUSTOMER_ID)).thenReturn(
                new Customer(CUSTOMER_ID, "Ada Lovelace", "ada@example.com", Instant.parse("2026-01-01T00:00:00Z")));
    }

    @State("no customer exists with id 22222222-2222-2222-2222-222222222222")
    void customerDoesNotExist() {
        when(customerApplicationService.getById(UNKNOWN_CUSTOMER_ID)).thenThrow(
                new ApiException(HttpStatus.NOT_FOUND, "CUST-4041", "Customer not found: " + UNKNOWN_CUSTOMER_ID));
    }

    @TestTemplate
    @ExtendWith(PactVerificationSpring7Provider.class)
    void pactVerificationTestTemplate(PactVerificationContext context) {
        context.verifyInteraction();
    }
}
