package com.company.kyc.api;

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
 * tests) {@code issuer-uri} — see CommonSecurityAutoConfiguration's Javadoc.
 */
class KycCheckControllerIntegrationTest extends AbstractMessagingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void requestingACheckRequiresAStaffRole() throws Exception {
        mockMvc.perform(post("/api/v1/kyc-checks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId": "%s"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isForbidden());
    }

    @Test
    void requestsAndFetchesAPendingCheck() throws Exception {
        UUID customerId = UUID.randomUUID();

        String location = requestCheck(customerId);

        mockMvc.perform(get(location).with(user("ops").roles("OPERATIONS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.customerId").value(customerId.toString()))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    @WithMockUser(roles = "OPERATIONS")
    void decisionRequiresComplianceRoleNotJustAnyStaffRole() throws Exception {
        String location = requestCheck(UUID.randomUUID());

        mockMvc.perform(post(location + "/decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"outcome": "APPROVED", "reason": "Looks fine"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void decidingApprovesAndRejectsSubsequentDecisionAsConflict() throws Exception {
        String location = mockMvc.perform(post("/api/v1/kyc-checks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId": "%s"}
                                """.formatted(UUID.randomUUID()))
                        .with(user("ops").roles("OPERATIONS")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");

        mockMvc.perform(post(location + "/decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"outcome": "APPROVED", "reason": "Application approved after review"}
                                """)
                        .with(user("compliance-officer").roles("COMPLIANCE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.reason").value("Application approved after review"));

        mockMvc.perform(post(location + "/decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"outcome": "REJECTED", "reason": "changed my mind"}
                                """)
                        .with(user("compliance-officer").roles("COMPLIANCE")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("KYC-4090"));
    }

    @Test
    @WithMockUser(roles = "COMPLIANCE")
    void returnsProblemDetailsForUnknownCheck() throws Exception {
        mockMvc.perform(get("/api/v1/kyc-checks/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("KYC-4041"));
    }

    @Test
    @WithMockUser(roles = "COMPLIANCE")
    void listsChecksForACustomer() throws Exception {
        UUID customerId = UUID.randomUUID();
        requestCheckAs(customerId, "OPERATIONS");
        requestCheckAs(customerId, "OPERATIONS");
        requestCheckAs(UUID.randomUUID(), "OPERATIONS"); // different customer — must not show up

        mockMvc.perform(get("/api/v1/kyc-checks").param("customerId", customerId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.meta.totalElements").value(2));
    }

    private String requestCheck(UUID customerId) throws Exception {
        return mockMvc.perform(post("/api/v1/kyc-checks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId": "%s"}
                                """.formatted(customerId))
                        .with(user("ops").roles("OPERATIONS")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
    }

    private void requestCheckAs(UUID customerId, String role) throws Exception {
        mockMvc.perform(post("/api/v1/kyc-checks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId": "%s"}
                                """.formatted(customerId))
                        .with(user("staff").roles(role)))
                .andExpect(status().isCreated());
    }
}
