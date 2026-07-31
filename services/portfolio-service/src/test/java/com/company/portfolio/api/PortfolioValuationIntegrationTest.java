package com.company.portfolio.api;

import com.company.platform.test.AbstractMessagingIntegrationTest;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves the valuation flow really calls Fund Service — the second real
 * inter-service consumer chain in this codebase (after Fund Service →
 * fund-mgmt-adapter) — against an embedded WireMock server standing in
 * for it. Fixed (not dynamic) port — see the adapters' own resilience
 * tests for why.
 */
@WithMockUser(roles = "OPERATIONS")
class PortfolioValuationIntegrationTest extends AbstractMessagingIntegrationTest {

    private static final int WIREMOCK_PORT = 9998;

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(WireMockConfiguration.wireMockConfig().port(WIREMOCK_PORT))
            .configureStaticDsl(true)
            .build();

    @DynamicPropertySource
    static void fundServiceBaseUrl(DynamicPropertyRegistry registry) {
        registry.add("fund-service.base-url", () -> "http://localhost:" + WIREMOCK_PORT);
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void valuatesAPortfolioAcrossMultiplePositions() throws Exception {
        String location = openPortfolio();
        recordPosition(location, "EQFND01", "100");
        recordPosition(location, "BONDFND", "50");

        stubFor(WireMock.get(urlPathMatching("/api/v1/funds/EQFND01/nav"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"success": true, "data": {"fundCode": "EQFND01", "navPerShare": 10.0000, "asOfDate": "2026-08-01"}}
                                """)));
        stubFor(WireMock.get(urlPathMatching("/api/v1/funds/BONDFND/nav"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"success": true, "data": {"fundCode": "BONDFND", "navPerShare": 100.0000, "asOfDate": "2026-08-01"}}
                                """)));

        mockMvc.perform(get(location + "/valuation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currency").value("USD"))
                .andExpect(jsonPath("$.data.positions.length()").value(2))
                .andExpect(jsonPath("$.data.totalValue").value(6000.0)); // 100*10 + 50*100
    }

    @Test
    void valuationFailsIfAnyPositionsFundHasNoNavData() throws Exception {
        String location = openPortfolio();
        recordPosition(location, "NONAVFUND", "10");

        stubFor(WireMock.get(urlPathMatching("/api/v1/funds/NONAVFUND/nav"))
                .willReturn(aResponse().withStatus(404).withHeader("Content-Type", "application/problem+json")
                        .withBody("""
                                {"status": 404, "errorCode": "FUND-4042"}
                                """)));

        mockMvc.perform(get(location + "/valuation"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("PORTFOLIO-4043"));
    }

    private String openPortfolio() throws Exception {
        return mockMvc.perform(post("/api/v1/portfolios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId": "%s", "ownerId": "%s", "name": "Retirement Account", "currency": "USD"}
                                """.formatted(UUID.randomUUID(), UUID.randomUUID()))
                        .with(user("ops").roles("OPERATIONS")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
    }

    private void recordPosition(String portfolioLocation, String fundCode, String quantity) throws Exception {
        mockMvc.perform(post(portfolioLocation + "/positions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fundCode": "%s", "quantity": %s}
                                """.formatted(fundCode, quantity))
                        .with(user("ops").roles("OPERATIONS")))
                .andExpect(status().isCreated());
    }
}
