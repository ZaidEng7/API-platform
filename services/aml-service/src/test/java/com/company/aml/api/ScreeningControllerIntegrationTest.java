package com.company.aml.api;

import com.company.platform.test.AbstractMessagingIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Real Postgres + RabbitMQ via Testcontainers (guide §20), with common-security's
 * method-level {@code @PreAuthorize} enforced regardless of the (empty in
 * tests) {@code issuer-uri}. The request endpoint returns {@code 202
 * Accepted} per guide §10.3's own async-operation example, not {@code 201}.
 */
class ScreeningControllerIntegrationTest extends AbstractMessagingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void requestingAScreeningRequiresAStaffRole() throws Exception {
        mockMvc.perform(post("/api/v1/aml/screenings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId": "%s"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isForbidden());
    }

    @Test
    void requestsAndFetchesAnInProgressScreening() throws Exception {
        UUID customerId = UUID.randomUUID();

        String location = requestScreening(customerId);

        mockMvc.perform(get(location).with(user("ops").roles("OPERATIONS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.customerId").value(customerId.toString()))
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));
    }

    @Test
    @WithMockUser(roles = "OPERATIONS")
    void resultRequiresComplianceRoleNotJustAnyStaffRole() throws Exception {
        String location = requestScreening(UUID.randomUUID());

        mockMvc.perform(post(location + "/result")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"outcome": "CLEAR", "notes": "Looks fine"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "COMPLIANCE")
    void failRequiresOperationsRoleNotCompliance() throws Exception {
        String location = requestScreening(UUID.randomUUID());

        mockMvc.perform(post(location + "/fail")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason": "Vendor adapter timeout"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void recordingResultCompletesAndRejectsSubsequentAttemptAsConflict() throws Exception {
        String location = requestScreening(UUID.randomUUID());

        mockMvc.perform(post(location + "/result")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"outcome": "CLEAR", "notes": "No watchlist match"}
                                """)
                        .with(user("compliance-officer").roles("COMPLIANCE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.outcome").value("CLEAR"));

        mockMvc.perform(post(location + "/result")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"outcome": "HIT", "notes": "changed my mind"}
                                """)
                        .with(user("compliance-officer").roles("COMPLIANCE")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("AML-4090"));
    }

    @Test
    void recordingFailureMarksScreeningFailed() throws Exception {
        String location = requestScreening(UUID.randomUUID());

        mockMvc.perform(post(location + "/fail")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason": "Vendor adapter timeout"}
                                """)
                        .with(user("ops").roles("OPERATIONS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andExpect(jsonPath("$.data.outcome").doesNotExist());
    }

    @Test
    @WithMockUser(roles = "COMPLIANCE")
    void returnsProblemDetailsForUnknownScreening() throws Exception {
        mockMvc.perform(get("/api/v1/aml/screenings/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("AML-4041"));
    }

    @Test
    @WithMockUser(roles = "COMPLIANCE")
    void listsScreeningsForACustomer() throws Exception {
        UUID customerId = UUID.randomUUID();
        requestScreeningAs(customerId, "OPERATIONS");
        requestScreeningAs(customerId, "OPERATIONS");
        requestScreeningAs(UUID.randomUUID(), "OPERATIONS"); // different customer — must not show up

        mockMvc.perform(get("/api/v1/aml/screenings").param("customerId", customerId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.meta.totalElements").value(2));
    }

    private String requestScreening(UUID customerId) throws Exception {
        return mockMvc.perform(post("/api/v1/aml/screenings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId": "%s"}
                                """.formatted(customerId))
                        .with(user("ops").roles("OPERATIONS")))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getHeader("Location");
    }

    private void requestScreeningAs(UUID customerId, String role) throws Exception {
        mockMvc.perform(post("/api/v1/aml/screenings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId": "%s"}
                                """.formatted(customerId))
                        .with(user("staff").roles(role)))
                .andExpect(status().isAccepted());
    }
}
