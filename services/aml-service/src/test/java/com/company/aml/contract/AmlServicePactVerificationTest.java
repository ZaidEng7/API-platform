package com.company.aml.contract;

import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.State;
import au.com.dius.pact.provider.junitsupport.loader.PactBroker;
import au.com.dius.pact.provider.spring.spring7.PactVerificationSpring7Provider;
import au.com.dius.pact.provider.spring.spring7.Spring7MockMvcTestTarget;
import com.company.aml.application.AmlScreeningApplicationService;
import com.company.aml.domain.AmlScreening;
import com.company.aml.domain.ScreeningOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Provider side of the Investment Service ↔ AML Service leg of the
 * subscription saga — verifies {@code ScreeningController} against
 * Investment Service's own {@code AmlScreeningClientPactTest}.
 */
@Tag("pact")
@Provider("aml-service")
@PactBroker(url = "${PACT_BROKER_URL:http://localhost:9292}")
@WithMockUser(roles = "OPERATIONS")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.autoconfigure.exclude="
                        + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
                        + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                        + "org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration,"
                        + "com.company.platform.messaging.autoconfigure.CommonMessagingAutoConfiguration,"
                        + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration"
        })
@AutoConfigureMockMvc
class AmlServicePactVerificationTest {

    private static final UUID CUSTOMER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SCREENING_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AmlScreeningApplicationService amlScreeningApplicationService;

    @BeforeEach
    void setUpTestTarget(PactVerificationContext context) {
        context.setTarget(new Spring7MockMvcTestTarget(mockMvc));
    }

    @State("customer 11111111-1111-1111-1111-111111111111 has a clear AML screening")
    void customerHasClearScreening() {
        var screening = new AmlScreening(SCREENING_ID, CUSTOMER_ID, Instant.parse("2026-01-01T00:00:00Z"));
        screening.complete(ScreeningOutcome.CLEAR, "No watchlist match");

        Page<AmlScreening> page = new PageImpl<>(List.of(screening));
        when(amlScreeningApplicationService.listByCustomer(eq(CUSTOMER_ID), any())).thenReturn(page);
    }

    @TestTemplate
    @ExtendWith(PactVerificationSpring7Provider.class)
    void pactVerificationTestTemplate(PactVerificationContext context) {
        context.verifyInteraction();
    }
}
