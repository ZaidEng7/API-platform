package com.company.customer.api;

import com.company.platform.test.AbstractMessagingIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Real Postgres + RabbitMQ via Testcontainers (guide §20) — exercises the
 * actual Flyway migration, JPA repository, controller, and common-web's
 * Problem Details / envelope handling end to end. RabbitMQ is required now
 * that {@code create()}/{@code update()} write outbox rows on the same
 * connection pool common-messaging's autoconfiguration wires up (see
 * CustomerEventPublishingIntegrationTest for the outbox→relay→RabbitMQ path
 * itself).
 */
class CustomerControllerIntegrationTest extends AbstractMessagingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void createsAndFetchesCustomer() throws Exception {
        String requestBody = """
                {"fullName": "Ada Lovelace", "email": "ada@example.com"}
                """;

        String location = mockMvc.perform(post("/api/v1/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.fullName").value("Ada Lovelace"))
                .andExpect(jsonPath("$.data.email").value("ada@example.com"))
                .andExpect(jsonPath("$.data.partyType").value("INDIVIDUAL")) // default when omitted
                .andReturn().getResponse().getHeader("Location");

        mockMvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("ada@example.com"));
    }

    @Test
    void createsCustomerWithFullPartyFields() throws Exception {
        String requestBody = """
                {"fullName": "Acme Corp", "email": "contact@acme.example.com", "phone": "+1-555-0100",
                 "dateOfBirth": "1990-05-14", "partyType": "ORGANIZATION"}
                """;

        mockMvc.perform(post("/api/v1/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.phone").value("+1-555-0100"))
                .andExpect(jsonPath("$.data.dateOfBirth").value("1990-05-14"))
                .andExpect(jsonPath("$.data.partyType").value("ORGANIZATION"));
    }

    @Test
    void returnsProblemDetailsForUnknownCustomer() throws Exception {
        mockMvc.perform(get("/api/v1/customers/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("CUST-4041"))
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void rejectsInvalidEmail() throws Exception {
        String requestBody = """
                {"fullName": "Bad Email", "email": "not-an-email"}
                """;

        mockMvc.perform(post("/api/v1/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    void updatesMutablePartyFieldsButNotEmailOrPartyType() throws Exception {
        String createBody = """
                {"fullName": "Grace Hopper", "email": "grace@example.com", "partyType": "INDIVIDUAL"}
                """;
        String location = mockMvc.perform(post("/api/v1/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");

        String updateBody = """
                {"fullName": "Grace Brewster Hopper", "phone": "+1-555-0199", "dateOfBirth": "1906-12-09"}
                """;
        mockMvc.perform(put(location)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fullName").value("Grace Brewster Hopper"))
                .andExpect(jsonPath("$.data.phone").value("+1-555-0199"))
                .andExpect(jsonPath("$.data.dateOfBirth").value("1906-12-09"))
                .andExpect(jsonPath("$.data.email").value("grace@example.com"))
                .andExpect(jsonPath("$.data.partyType").value("INDIVIDUAL"));
    }

    @Test
    void returnsProblemDetailsWhenUpdatingUnknownCustomer() throws Exception {
        String updateBody = """
                {"fullName": "Nobody"}
                """;
        mockMvc.perform(put("/api/v1/customers/{id}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("CUST-4041"));
    }

    @Test
    void searchFiltersByFullNameOrEmail() throws Exception {
        mockMvc.perform(post("/api/v1/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"fullName": "Katherine Johnson", "email": "katherine@example.com"}
                        """))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"fullName": "Dorothy Vaughan", "email": "dorothy@example.com"}
                        """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/customers").param("query", "Katherine"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].fullName").value("Katherine Johnson"))
                .andExpect(jsonPath("$.meta.totalElements").value(1));
    }
}
