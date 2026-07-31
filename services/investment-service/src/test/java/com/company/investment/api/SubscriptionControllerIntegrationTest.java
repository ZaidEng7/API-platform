package com.company.investment.api;

import com.company.platform.test.AbstractMessagingIntegrationTest;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Real Postgres + RabbitMQ via Testcontainers (guide §20). All four
 * downstream services (Customer/KYC/AML/Portfolio) share one embedded
 * WireMock server, distinguished by path — their real paths never
 * collide. Fixed (not dynamic) WireMock port — see the adapters' own
 * resilience tests for why.
 */
class SubscriptionControllerIntegrationTest extends AbstractMessagingIntegrationTest {

    private static final int WIREMOCK_PORT = 9999;

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(WireMockConfiguration.wireMockConfig().port(WIREMOCK_PORT))
            .configureStaticDsl(true)
            .build();

    @DynamicPropertySource
    static void externalServiceUrls(DynamicPropertyRegistry registry) {
        String base = "http://localhost:" + WIREMOCK_PORT;
        registry.add("customer-service.base-url", () -> base);
        registry.add("kyc-service.base-url", () -> base);
        registry.add("aml-service.base-url", () -> base);
        registry.add("portfolio-service.base-url", () -> base);
    }

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void resetStubs() {
        wireMock.resetAll();
    }

    @Test
    void requestingASubscriptionRequiresAStaffRole() throws Exception {
        mockMvc.perform(post("/api/v1/subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .content(subscriptionRequestBody(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "OPERATIONS")
    void requestingWithoutIdempotencyKeyHeaderIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(subscriptionRequestBody(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    @WithMockUser(roles = "OPERATIONS")
    void requestingForAnUnknownCustomerIs404BeforeAnyOtherCheck() throws Exception {
        UUID customerId = UUID.randomUUID();
        stubFor(WireMock.get(urlPathMatching("/api/v1/customers/" + customerId))
                .willReturn(aResponse().withStatus(404)));

        mockMvc.perform(post("/api/v1/subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .content(subscriptionRequestBody(customerId, UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("INV-4044"));
    }

    @Test
    @WithMockUser(roles = "OPERATIONS")
    void successfulComplianceChecksLandTheSubscriptionAwaitingPayment() throws Exception {
        UUID customerId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID portfolioId = UUID.randomUUID();
        stubHappyPathChecks(customerId);

        mockMvc.perform(post("/api/v1/subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .content(subscriptionRequestBody(customerId, ownerId, portfolioId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("AWAITING_PAYMENT"))
                .andExpect(jsonPath("$.data.customerId").value(customerId.toString()));
    }

    @Test
    @WithMockUser(roles = "OPERATIONS")
    void failedKycCheckLandsTheSubscriptionFailedWithAReason() throws Exception {
        UUID customerId = UUID.randomUUID();
        stubCustomerExists(customerId);
        stubKycStatus(customerId, "PENDING");
        stubAmlClear(customerId);

        mockMvc.perform(post("/api/v1/subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .content(subscriptionRequestBody(customerId, UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andExpect(jsonPath("$.data.failureReason").value("KYC not approved"));
    }

    @Test
    @WithMockUser(roles = "OPERATIONS")
    void replayingTheSameIdempotencyKeyReturnsTheOriginalSubscriptionWithoutRepeatingChecks() throws Exception {
        UUID customerId = UUID.randomUUID();
        String idempotencyKey = UUID.randomUUID().toString();
        stubHappyPathChecks(customerId);
        String requestBody = subscriptionRequestBody(customerId, UUID.randomUUID(), UUID.randomUUID());

        String firstId = mockMvc.perform(post("/api/v1/subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", idempotencyKey)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");

        mockMvc.perform(post("/api/v1/subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", idempotencyKey)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(firstId.substring(firstId.lastIndexOf('/') + 1)));

        wireMock.verify(1, getRequestedFor(urlPathMatching("/api/v1/customers/" + customerId)));
    }

    @Test
    @WithMockUser(roles = "OPERATIONS")
    void confirmingPaymentMaterializesThePositionAndConfirms() throws Exception {
        UUID customerId = UUID.randomUUID();
        stubHappyPathChecks(customerId);
        String location = requestSubscription(customerId, UUID.randomUUID(), UUID.randomUUID());
        stubFor(WireMock.post(urlPathMatching("/api/v1/portfolios/.*/positions")).willReturn(aResponse().withStatus(201)));

        mockMvc.perform(post(location + "/confirm-payment").with(user("ops").roles("OPERATIONS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"));

        mockMvc.perform(post(location + "/confirm-payment").with(user("ops").roles("OPERATIONS")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("INV-4091"));
    }

    @Test
    @WithMockUser(roles = "OPERATIONS")
    void cancellingReleasesTheReservationAndRejectsASecondCancel() throws Exception {
        UUID customerId = UUID.randomUUID();
        stubHappyPathChecks(customerId);
        String location = requestSubscription(customerId, UUID.randomUUID(), UUID.randomUUID());

        mockMvc.perform(post(location + "/cancel").with(user("ops").roles("OPERATIONS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        mockMvc.perform(post(location + "/cancel").with(user("ops").roles("OPERATIONS")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("INV-4091"));
    }

    @Test
    void investorCanViewTheirOwnSubscriptionButNotAnotherInvestors() throws Exception {
        UUID customerId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID someoneElse = UUID.randomUUID();
        stubHappyPathChecks(customerId);
        String location = mockMvc.perform(post("/api/v1/subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .content(subscriptionRequestBody(customerId, ownerId, UUID.randomUUID()))
                        .with(user("ops").roles("OPERATIONS")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");

        mockMvc.perform(get(location).with(jwt().jwt(j -> j.subject(ownerId.toString()))
                        .authorities(new SimpleGrantedAuthority("ROLE_INVESTOR"))))
                .andExpect(status().isOk());

        mockMvc.perform(get(location).with(jwt().jwt(j -> j.subject(someoneElse.toString()))
                        .authorities(new SimpleGrantedAuthority("ROLE_INVESTOR"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("INV-4030"));
    }

    @Test
    @WithMockUser(roles = "COMPLIANCE")
    void returnsProblemDetailsForUnknownSubscription() throws Exception {
        mockMvc.perform(get("/api/v1/subscriptions/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("INV-4041"));
    }

    private String requestSubscription(UUID customerId, UUID ownerId, UUID portfolioId) throws Exception {
        return mockMvc.perform(post("/api/v1/subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .content(subscriptionRequestBody(customerId, ownerId, portfolioId))
                        .with(user("ops").roles("OPERATIONS")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
    }

    private String subscriptionRequestBody(UUID customerId, UUID ownerId, UUID portfolioId) {
        return """
                {"customerId": "%s", "ownerId": "%s", "portfolioId": "%s", "fundCode": "EQFND01", "quantity": 100}
                """.formatted(customerId, ownerId, portfolioId);
    }

    private void stubHappyPathChecks(UUID customerId) {
        stubCustomerExists(customerId);
        stubKycStatus(customerId, "APPROVED");
        stubAmlClear(customerId);
    }

    private void stubCustomerExists(UUID customerId) {
        stubFor(WireMock.get(urlPathMatching("/api/v1/customers/" + customerId))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"success": true, "data": {"id": "%s"}}
                                """.formatted(customerId))));
    }

    private void stubKycStatus(UUID customerId, String status) {
        stubFor(WireMock.get(urlPathMatching("/api/v1/kyc-checks.*"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"success": true, "data": [{"status": "%s"}], "meta": {"page": 0, "size": 1, "totalElements": 1, "totalPages": 1}}
                                """.formatted(status))));
    }

    private void stubAmlClear(UUID customerId) {
        stubFor(WireMock.get(urlPathMatching("/api/v1/aml/screenings.*"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"success": true, "data": [{"status": "COMPLETED", "outcome": "CLEAR"}], "meta": {"page": 0, "size": 1, "totalElements": 1, "totalPages": 1}}
                                """)));
    }
}
