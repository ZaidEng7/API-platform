package com.company.document.api;

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
 * tests) {@code issuer-uri}.
 */
class DocumentControllerIntegrationTest extends AbstractMessagingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void uploadingADocumentRequiresAStaffRole() throws Exception {
        mockMvc.perform(post("/api/v1/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId": "%s", "documentType": "PASSPORT", "storageReference": "dms://ref-1"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isForbidden());
    }

    @Test
    void uploadsAndFetchesADocument() throws Exception {
        UUID customerId = UUID.randomUUID();

        String location = uploadDocument(customerId, "dms://ref-1");

        mockMvc.perform(get(location).with(user("ops").roles("OPERATIONS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.customerId").value(customerId.toString()))
                .andExpect(jsonPath("$.data.documentType").value("PASSPORT"))
                .andExpect(jsonPath("$.data.storageReference").value("dms://ref-1"))
                .andExpect(jsonPath("$.data.status").value("UPLOADED"));
    }

    @Test
    @WithMockUser(roles = "OPERATIONS")
    void verifyRequiresComplianceRoleNotJustAnyStaffRole() throws Exception {
        String location = uploadDocument(UUID.randomUUID(), "dms://ref-1");

        mockMvc.perform(post(location + "/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"notes": "Looks fine"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void verifyingCompletesAndRejectsSubsequentReviewAsConflict() throws Exception {
        String location = mockMvc.perform(post("/api/v1/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId": "%s", "documentType": "PASSPORT", "storageReference": "dms://ref-1"}
                                """.formatted(UUID.randomUUID()))
                        .with(user("ops").roles("OPERATIONS")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");

        mockMvc.perform(post(location + "/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"notes": "Clear scan, matches name on file"}
                                """)
                        .with(user("compliance-officer").roles("COMPLIANCE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("VERIFIED"))
                .andExpect(jsonPath("$.data.notes").value("Clear scan, matches name on file"));

        mockMvc.perform(post(location + "/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"notes": "changed my mind"}
                                """)
                        .with(user("compliance-officer").roles("COMPLIANCE")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DOC-4090"));
    }

    @Test
    void rejectingMarksDocumentRejected() throws Exception {
        String location = uploadDocument(UUID.randomUUID(), "dms://ref-1");

        mockMvc.perform(post(location + "/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"notes": "Expired"}
                                """)
                        .with(user("compliance-officer").roles("COMPLIANCE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.notes").value("Expired"));
    }

    @Test
    @WithMockUser(roles = "COMPLIANCE")
    void returnsProblemDetailsForUnknownDocument() throws Exception {
        mockMvc.perform(get("/api/v1/documents/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("DOC-4041"));
    }

    @Test
    @WithMockUser(roles = "COMPLIANCE")
    void listsDocumentsForACustomer() throws Exception {
        UUID customerId = UUID.randomUUID();
        uploadDocumentAs(customerId, "OPERATIONS");
        uploadDocumentAs(customerId, "OPERATIONS");
        uploadDocumentAs(UUID.randomUUID(), "OPERATIONS"); // different customer — must not show up

        mockMvc.perform(get("/api/v1/documents").param("customerId", customerId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.meta.totalElements").value(2));
    }

    private String uploadDocument(UUID customerId, String storageReference) throws Exception {
        return mockMvc.perform(post("/api/v1/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId": "%s", "documentType": "PASSPORT", "storageReference": "%s"}
                                """.formatted(customerId, storageReference))
                        .with(user("ops").roles("OPERATIONS")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
    }

    private void uploadDocumentAs(UUID customerId, String role) throws Exception {
        mockMvc.perform(post("/api/v1/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId": "%s", "documentType": "PASSPORT", "storageReference": "dms://ref"}
                                """.formatted(customerId))
                        .with(user("staff").roles(role)))
                .andExpect(status().isCreated());
    }
}
