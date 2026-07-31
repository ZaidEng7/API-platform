package com.company.fundmgmtadapter.api;

import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Real HTTP round-trip through the whole adapter (controller -> cache ->
 * resilience layer -> translation), against a stub legacy backend —
 * proves the anti-corruption boundary holds: the response is a real
 * decimal, not a legacy scaled integer.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class FundNavControllerIntegrationTest {

    // Fixed, not dynamic — see FundNavProviderCachingTest for why.
    private static final int WIREMOCK_PORT = 9991;

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(WireMockConfiguration.wireMockConfig().port(WIREMOCK_PORT))
            .configureStaticDsl(true)
            .build();

    @DynamicPropertySource
    static void legacyFundMgmtBaseUrl(DynamicPropertyRegistry registry) {
        registry.add("legacy-fund-mgmt.base-url", () -> "http://localhost:" + WIREMOCK_PORT);
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void translatesLegacyResponseToCanonicalShape() throws Exception {
        stubFor(get(urlPathMatching("/fundmgmt/v1/funds/.*/nav"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"FUND_CD": "EQFND01", "NAV_VALUE_X10000": 105023, "NAV_DT_YYYYMMDD": "20260730"}
                                """)));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/funds/EQFND01/nav"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fundCode").value("EQFND01"))
                .andExpect(jsonPath("$.data.navPerShare").value(10.5023))
                .andExpect(jsonPath("$.data.asOfDate").value("2026-07-30"));
    }
}
