package com.company.fund.api;

import com.company.platform.test.AbstractMessagingIntegrationTest;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves Fund Service's NAV-refresh flow really calls
 * {@code integration/fund-mgmt-adapter} over HTTP — the first real
 * consumer of a Phase 4 adapter in this codebase — against an embedded
 * WireMock server standing in for that adapter. Fixed (not dynamic) port —
 * see the adapters' own resilience tests for why
 * ({@code @DynamicPropertySource} runs before {@code @RegisterExtension}'s
 * {@code beforeAll()} binds a port).
 */
class FundNavRefreshIntegrationTest extends AbstractMessagingIntegrationTest {

    private static final int WIREMOCK_PORT = 9996;

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(WireMockConfiguration.wireMockConfig().port(WIREMOCK_PORT))
            .configureStaticDsl(true)
            .build();

    @DynamicPropertySource
    static void fundMgmtAdapterBaseUrl(DynamicPropertyRegistry registry) {
        registry.add("fund-mgmt-adapter.base-url", () -> "http://localhost:" + WIREMOCK_PORT);
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void refreshingNavCallsTheAdapterAndStoresASnapshot() throws Exception {
        registerFund("EQFND10", "Global Equity Fund", "USD");
        stubFor(get(urlPathMatching("/api/v1/funds/EQFND10/nav"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"success": true, "data": {"fundCode": "EQFND10", "navPerShare": 10.5023, "asOfDate": "2026-07-30"}}
                                """)));

        mockMvc.perform(post("/api/v1/funds/{fundCode}/nav/refresh", "EQFND10")
                        .with(user("ops").roles("OPERATIONS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fundCode").value("EQFND10"))
                .andExpect(jsonPath("$.data.navPerShare").value(10.5023))
                .andExpect(jsonPath("$.data.asOfDate").value("2026-07-30"));

        mockMvc.perform(get("/api/v1/funds/{fundCode}/nav", "EQFND10").with(user("ops").roles("OPERATIONS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.navPerShare").value(10.5023));
    }

    @Test
    void refreshingNavForAnUnknownFundIs404BeforeEverCallingTheAdapter() throws Exception {
        mockMvc.perform(post("/api/v1/funds/{fundCode}/nav/refresh", "NOSUCHFUND")
                        .with(user("ops").roles("OPERATIONS")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("FUND-4041"));

        wireMock.verify(0, getRequestedFor(urlPathMatching("/api/v1/funds/NOSUCHFUND/nav")));
    }

    @Test
    void returns503WhenTheAdapterIsUnavailable() throws Exception {
        registerFund("EQFND11", "Bond Fund", "USD");
        stubFor(get(urlPathMatching("/api/v1/funds/EQFND11/nav")).willReturn(aResponse().withStatus(500)));

        mockMvc.perform(post("/api/v1/funds/{fundCode}/nav/refresh", "EQFND11")
                        .with(user("ops").roles("OPERATIONS")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errorCode").value("FUND-5031"));
    }

    @Test
    void getLatestNavIs404UntilARefreshHasHappened() throws Exception {
        registerFund("EQFND12", "Frontier Fund", "USD");

        mockMvc.perform(get("/api/v1/funds/{fundCode}/nav", "EQFND12").with(user("ops").roles("OPERATIONS")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("FUND-4042"));
    }

    private void registerFund(String fundCode, String name, String currency) throws Exception {
        mockMvc.perform(post("/api/v1/funds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fundCode": "%s", "name": "%s", "currency": "%s"}
                                """.formatted(fundCode, name, currency))
                        .with(user("ops").roles("OPERATIONS")))
                .andExpect(status().isCreated());
    }
}
