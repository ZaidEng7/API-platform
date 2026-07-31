package com.company.fund.api;

import com.company.platform.test.AbstractMessagingIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Real Postgres + RabbitMQ via Testcontainers (guide §20), with common-security's
 * method-level {@code @PreAuthorize} enforced regardless of the (empty in
 * tests) {@code issuer-uri}. NAV-refresh flows (which call out to
 * fund-mgmt-adapter) are covered separately in FundNavRefreshIntegrationTest,
 * where WireMock stubs that call.
 */
class FundControllerIntegrationTest extends AbstractMessagingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void registeringAFundRequiresAStaffRole() throws Exception {
        mockMvc.perform(post("/api/v1/funds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fundCode": "EQFND01", "name": "Global Equity Fund", "currency": "USD"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void registersAndFetchesAFund() throws Exception {
        String location = registerFund("EQFND02", "International Equity Fund", "GBP");

        mockMvc.perform(get(location).with(user("ops").roles("OPERATIONS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fundCode").value("EQFND02"))
                .andExpect(jsonPath("$.data.name").value("International Equity Fund"))
                .andExpect(jsonPath("$.data.currency").value("GBP"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    void rejectsInvalidCurrencyCode() throws Exception {
        mockMvc.perform(post("/api/v1/funds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fundCode": "BADFND", "name": "Bad Fund", "currency": "usd"}
                                """)
                        .with(user("ops").roles("OPERATIONS")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    @WithMockUser(roles = "OPERATIONS")
    void registeringTheSameFundCodeTwiceIsAConflict() throws Exception {
        String requestBody = """
                {"fundCode": "EQFND03", "name": "Emerging Markets Fund", "currency": "USD"}
                """;

        mockMvc.perform(post("/api/v1/funds").contentType(MediaType.APPLICATION_JSON).content(requestBody))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/funds").contentType(MediaType.APPLICATION_JSON).content(requestBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("FUND-4091"));
    }

    @Test
    @WithMockUser(roles = "PORTFOLIO_MANAGER")
    void returnsProblemDetailsForUnknownFund() throws Exception {
        mockMvc.perform(get("/api/v1/funds/{fundCode}", "NOSUCHFUND"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("FUND-4041"));
    }

    @Test
    @WithMockUser(roles = "AUDITOR")
    void listsRegisteredFunds() throws Exception {
        registerFund("EQFND04", "Small Cap Fund", "USD");

        mockMvc.perform(get("/api/v1/funds").param("size", "200"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.totalElements").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    }

    private String registerFund(String fundCode, String name, String currency) throws Exception {
        return mockMvc.perform(post("/api/v1/funds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fundCode": "%s", "name": "%s", "currency": "%s"}
                                """.formatted(fundCode, name, currency))
                        .with(user("ops").roles("OPERATIONS")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
    }
}
