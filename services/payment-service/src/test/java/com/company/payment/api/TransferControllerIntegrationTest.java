package com.company.payment.api;

import com.company.platform.test.AbstractMessagingIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Real Postgres + RabbitMQ via Testcontainers (guide §20). No downstream
 * REST calls exist in this service (no real PSP adapter — see {@code
 * Transfer}'s Javadoc), so unlike Investment/Fund/Portfolio Service's own
 * integration tests, no WireMock stub server is needed here.
 */
class TransferControllerIntegrationTest extends AbstractMessagingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void requestingATransferRequiresAStaffRole() throws Exception {
        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .content(transferRequestBody(UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "OPERATIONS")
    void requestingWithoutIdempotencyKeyHeaderIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferRequestBody(UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    @WithMockUser(roles = "OPERATIONS")
    void requestingWithARawCardNumberInsteadOfAPspTokenIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .content("""
                                {"customerId": "%s", "ownerId": "%s", "amount": 100.00, "currency": "USD",
                                 "paymentMethodToken": "4111111111111111"}
                                """.formatted(UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    @WithMockUser(roles = "OPERATIONS")
    void requestingATransferReturns202WithLocationAndPendingStatus() throws Exception {
        UUID customerId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .content(transferRequestBody(customerId, ownerId)))
                .andExpect(status().isAccepted())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.customerId").value(customerId.toString()));
    }

    @Test
    @WithMockUser(roles = "OPERATIONS")
    void replayingTheSameIdempotencyKeyReturnsTheOriginalTransfer() throws Exception {
        String idempotencyKey = UUID.randomUUID().toString();
        String requestBody = transferRequestBody(UUID.randomUUID(), UUID.randomUUID());

        String firstId = mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", idempotencyKey)
                        .content(requestBody))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getHeader("Location");

        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", idempotencyKey)
                        .content(requestBody))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.id").value(firstId.substring(firstId.lastIndexOf('/') + 1)));
    }

    @Test
    @WithMockUser(roles = "PORTFOLIO_MANAGER")
    void settlingRequiresOperationsRoleNotJustAnyStaffRole() throws Exception {
        String location = requestTransfer(UUID.randomUUID(), UUID.randomUUID());

        mockMvc.perform(post(location + "/settle").with(user("pm").roles("PORTFOLIO_MANAGER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void settlingMaterializesTheTransferAndRejectsASecondSettle() throws Exception {
        String location = requestTransfer(UUID.randomUUID(), UUID.randomUUID());

        mockMvc.perform(post(location + "/settle").with(user("ops").roles("OPERATIONS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SETTLED"));

        mockMvc.perform(post(location + "/settle").with(user("ops").roles("OPERATIONS")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("PAY-4090"));
    }

    @Test
    void failingRecordsAReasonAndRejectsASecondAttempt() throws Exception {
        String location = requestTransfer(UUID.randomUUID(), UUID.randomUUID());

        mockMvc.perform(post(location + "/fail")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason": "Card declined by issuer"}
                                """)
                        .with(user("ops").roles("OPERATIONS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andExpect(jsonPath("$.data.failureReason").value("Card declined by issuer"));

        mockMvc.perform(post(location + "/settle").with(user("ops").roles("OPERATIONS")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("PAY-4090"));
    }

    @Test
    void investorCanViewTheirOwnTransferButNotAnotherInvestors() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID someoneElse = UUID.randomUUID();
        String location = requestTransfer(UUID.randomUUID(), ownerId);

        mockMvc.perform(get(location).with(jwt().jwt(j -> j.subject(ownerId.toString()))
                        .authorities(new SimpleGrantedAuthority("ROLE_INVESTOR"))))
                .andExpect(status().isOk());

        mockMvc.perform(get(location).with(jwt().jwt(j -> j.subject(someoneElse.toString()))
                        .authorities(new SimpleGrantedAuthority("ROLE_INVESTOR"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("PAY-4030"));
    }

    @Test
    void investorCannotListAnotherInvestorsTransfersByOwnerId() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID someoneElse = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/payments").param("ownerId", ownerId.toString())
                        .with(jwt().jwt(j -> j.subject(someoneElse.toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_INVESTOR"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("PAY-4030"));
    }

    @Test
    @WithMockUser(roles = "COMPLIANCE")
    void returnsProblemDetailsForUnknownTransfer() throws Exception {
        mockMvc.perform(get("/api/v1/payments/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("PAY-4041"));
    }

    private String requestTransfer(UUID customerId, UUID ownerId) throws Exception {
        return mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .content(transferRequestBody(customerId, ownerId))
                        .with(user("ops").roles("OPERATIONS")))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getHeader("Location");
    }

    private String transferRequestBody(UUID customerId, UUID ownerId) {
        return """
                {"customerId": "%s", "ownerId": "%s", "amount": 100.00, "currency": "USD",
                 "paymentMethodToken": "tok_visa_1234", "reference": "subscription-ref"}
                """.formatted(customerId, ownerId);
    }
}
