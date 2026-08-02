package com.company.portfolio.contract;

import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.State;
import au.com.dius.pact.provider.junitsupport.loader.PactBroker;
import au.com.dius.pact.provider.spring.spring7.PactVerificationSpring7Provider;
import au.com.dius.pact.provider.spring.spring7.Spring7MockMvcTestTarget;
import com.company.portfolio.application.PortfolioApplicationService;
import com.company.portfolio.domain.Position;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Provider side of the Investment Service ↔ Portfolio Service pair — the
 * subscription saga's "confirm" step. Verifies {@code PortfolioController}
 * against Investment Service's own {@code PortfolioPositionClientPactTest}.
 */
@Tag("pact")
@Provider("portfolio-service")
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
class PortfolioServicePactVerificationTest {

    private static final UUID PORTFOLIO_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID POSITION_ID = UUID.fromString("66666666-6666-6666-6666-666666666666");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PortfolioApplicationService portfolioApplicationService;

    @BeforeEach
    void setUpTestTarget(PactVerificationContext context) {
        context.setTarget(new Spring7MockMvcTestTarget(mockMvc));
    }

    @State("portfolio 55555555-5555-5555-5555-555555555555 exists")
    void portfolioExists() {
        when(portfolioApplicationService.recordPosition(eq(PORTFOLIO_ID), eq("EQFND01"), eq(new BigDecimal("100")), any()))
                .thenReturn(new Position(POSITION_ID, PORTFOLIO_ID, "EQFND01", new BigDecimal("100"),
                        Instant.parse("2026-08-01T00:00:00Z"), null));
    }

    @TestTemplate
    @ExtendWith(PactVerificationSpring7Provider.class)
    void pactVerificationTestTemplate(PactVerificationContext context) {
        context.verifyInteraction();
    }
}
