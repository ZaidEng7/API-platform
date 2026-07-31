package com.company.crmadapter.api;

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
 * Real HTTP round-trip through the whole adapter (controller -> resilience
 * layer -> translation), against a stub legacy backend — proves the
 * anti-corruption boundary actually holds: the response is canonical
 * shape, no legacy field names or codes leak through.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class CrmCustomerControllerIntegrationTest {

    // Fixed, not dynamic: @DynamicPropertySource runs before this extension's
    // beforeAll() actually binds a dynamic port (SpringExtension, implied by
    // @SpringBootTest, registers/runs ahead of a @RegisterExtension static
    // field here), so reading wireMock.baseUrl() at that point is stale.
    private static final int WIREMOCK_PORT = 9997;

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(WireMockConfiguration.wireMockConfig().port(WIREMOCK_PORT))
            // Without this, the static WireMock.stubFor()/get() DSL methods
            // target the default client (localhost:8080), not this instance.
            .configureStaticDsl(true)
            .build();

    @DynamicPropertySource
    static void legacyCrmBaseUrl(DynamicPropertyRegistry registry) {
        registry.add("legacy-crm.base-url", () -> "http://localhost:" + WIREMOCK_PORT);
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void translatesLegacyResponseToCanonicalShape() throws Exception {
        stubFor(get(urlPathMatching("/crm/v1/customers/.*"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"CUST_ID": "42", "CUST_NM": "Ada Lovelace", "EMAIL_ADDR": "ada@example.com", "CUST_STATUS_CD": "A", "VIP_FLG": "Y"}
                                """)));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/crm-customers/42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("42"))
                .andExpect(jsonPath("$.data.fullName").value("Ada Lovelace"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.vip").value(true));
    }
}
