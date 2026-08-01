package com.company.fund.contract;

import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.State;
import au.com.dius.pact.provider.junitsupport.loader.PactBroker;
import au.com.dius.pact.provider.spring.spring7.PactVerificationSpring7Provider;
import au.com.dius.pact.provider.spring.spring7.Spring7MockMvcTestTarget;
import com.company.fund.application.FundApplicationService;
import com.company.fund.domain.NavSnapshot;
import com.company.platform.web.exception.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.mockito.Mockito.when;

/**
 * Provider side of the Portfolio Service ↔ Fund Service pair — verifies
 * the real {@code FundController} against Portfolio Service's own
 * {@code FundNavClientPactTest}. {@code @WithMockUser(roles =
 * "PORTFOLIO_MANAGER")} at class level satisfies {@code GET .../nav}'s own
 * {@code @PreAuthorize} gate for every interaction — this pact is
 * read-only, so one role suffices for all of them, unlike a pact spanning
 * both read and write endpoints.
 */
@Tag("pact")
@Provider("fund-service")
@PactBroker(url = "${PACT_BROKER_URL:http://localhost:9292}")
@WithMockUser(roles = "PORTFOLIO_MANAGER")
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
class FundServicePactVerificationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FundApplicationService fundApplicationService;

    @BeforeEach
    void setUpTestTarget(PactVerificationContext context) {
        context.setTarget(new Spring7MockMvcTestTarget(mockMvc));
    }

    @State("fund EQFND01 has a current NAV")
    void fundHasCurrentNav() {
        when(fundApplicationService.getLatestNav("EQFND01")).thenReturn(new NavSnapshot(UUID.randomUUID(),
                "EQFND01", new BigDecimal("12.3456"), LocalDate.of(2026, 8, 1), Instant.now()));
    }

    @State("fund NONAV01 exists but has no NAV yet")
    void fundHasNoNavYet() {
        when(fundApplicationService.getLatestNav("NONAV01")).thenThrow(
                new ApiException(HttpStatus.NOT_FOUND, "FUND-4042", "No NAV snapshot yet for fund: NONAV01"));
    }

    @TestTemplate
    @ExtendWith(PactVerificationSpring7Provider.class)
    void pactVerificationTestTemplate(PactVerificationContext context) {
        context.verifyInteraction();
    }
}
