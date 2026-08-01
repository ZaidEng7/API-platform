package com.company.fundmgmtadapter.contract;

import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.State;
import au.com.dius.pact.provider.junitsupport.loader.PactBroker;
import au.com.dius.pact.provider.spring.spring7.PactVerificationSpring7Provider;
import au.com.dius.pact.provider.spring.spring7.Spring7MockMvcTestTarget;
import com.company.fundmgmtadapter.api.dto.FundNavResponse;
import com.company.fundmgmtadapter.legacy.FundNavProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.mockito.Mockito.when;

/**
 * Provider side of the Fund Service ↔ fund-mgmt-adapter pair — the first
 * real Pact pair in this codebase (see contracts/README.md), replacing the
 * template. {@link FundNavProvider} (the cache/resilience-facing bean) is
 * mocked so this doesn't need a live legacy backend stub, mirroring how
 * customer-service's own provider verification mocks its application
 * service.
 */
@Tag("pact")
@Provider("fund-mgmt-adapter")
@PactBroker(url = "${PACT_BROKER_URL:http://localhost:9292}")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class FundMgmtAdapterPactVerificationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FundNavProvider fundNavProvider;

    @BeforeEach
    void setUpTestTarget(PactVerificationContext context) {
        context.setTarget(new Spring7MockMvcTestTarget(mockMvc));
    }

    @State("fund EQFND01 has a current NAV")
    void fundHasCurrentNav() {
        when(fundNavProvider.getNav("EQFND01"))
                .thenReturn(new FundNavResponse("EQFND01", new BigDecimal("12.3456"), LocalDate.of(2026, 8, 1)));
    }

    @TestTemplate
    @ExtendWith(PactVerificationSpring7Provider.class)
    void pactVerificationTestTemplate(PactVerificationContext context) {
        context.verifyInteraction();
    }
}
