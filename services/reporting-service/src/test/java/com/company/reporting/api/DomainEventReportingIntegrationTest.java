package com.company.reporting.api;

import com.company.platform.messaging.envelope.EventEnvelope;
import com.company.platform.test.AbstractMessagingIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Real Postgres + RabbitMQ (guide §20). This service produces no domain
 * events of its own, so — same approach Audit Service's own integration
 * test uses — this test plays producer directly: publishes real
 * {@link EventEnvelope}-shaped messages onto the actual {@code domain-events}
 * exchange (the same shape every Phase 5 service's outbox relay actually
 * puts on the wire) and asserts the full path: consume, materialize the
 * read-model, serve it back over REST.
 */
class DomainEventReportingIntegrationTest extends AbstractMessagingIntegrationTest {

    private static final String EXCHANGE = "domain-events";

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser(roles = "OPERATIONS")
    void fundRegistrationAndNavUpdateMaterializeIntoOneFundView() throws Exception {
        String fundCode = "EQFND" + UUID.randomUUID().toString().substring(0, 6);

        publish("fund.definition.registered", Map.of(
                "fundId", UUID.randomUUID(),
                "fundCode", fundCode,
                "name", "Global Equity Fund",
                "currency", "USD",
                "registeredAt", Instant.now()));
        publish("fund.nav.updated", Map.of(
                "fundCode", fundCode,
                "navPerShare", new BigDecimal("12.3456"),
                "asOfDate", LocalDate.of(2026, 8, 1),
                "fetchedAt", Instant.now()));

        await().pollInSameThread().atMost(15, TimeUnit.SECONDS).untilAsserted(() ->
                mockMvc.perform(get("/api/v1/reports/funds/{fundCode}", fundCode))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.name").value("Global Equity Fund"))
                        .andExpect(jsonPath("$.data.navPerShare").value(12.3456)));
    }

    @Test
    @WithMockUser(roles = "COMPLIANCE")
    void portfolioOpenedAndPositionRecordedMaterializeIntoAPortfolioDetail() throws Exception {
        UUID portfolioId = UUID.randomUUID();
        UUID positionId = UUID.randomUUID();

        publish("portfolio.account.opened", Map.of(
                "portfolioId", portfolioId,
                "customerId", UUID.randomUUID(),
                "ownerId", UUID.randomUUID(),
                "name", "Retirement Account",
                "currency", "USD",
                "openedAt", Instant.now()));
        publish("portfolio.position.recorded", Map.of(
                "positionId", positionId,
                "portfolioId", portfolioId,
                "fundCode", "EQFND01",
                "quantity", new BigDecimal("100.0000"),
                "recordedAt", Instant.now()));

        await().pollInSameThread().atMost(15, TimeUnit.SECONDS).untilAsserted(() ->
                mockMvc.perform(get("/api/v1/reports/portfolios/{id}", portfolioId))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.name").value("Retirement Account"))
                        .andExpect(jsonPath("$.data.positions.length()").value(1))
                        .andExpect(jsonPath("$.data.positions[0].fundCode").value("EQFND01")));
    }

    @Test
    @WithMockUser(roles = "AUDITOR")
    void transferRequestedThenSettledMaterializesAsSettledInThePaymentReport() throws Exception {
        UUID transferId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();

        publish("payment.transfer.requested", Map.of(
                "transferId", transferId,
                "customerId", customerId,
                "amount", new BigDecimal("500.00"),
                "currency", "USD",
                "requestedAt", Instant.now()));
        publish("payment.transfer.settled", Map.of(
                "transferId", transferId,
                "customerId", customerId,
                "amount", new BigDecimal("500.00"),
                "currency", "USD",
                "settledAt", Instant.now()));

        await().pollInSameThread().atMost(15, TimeUnit.SECONDS).untilAsserted(() ->
                mockMvc.perform(get("/api/v1/reports/payments").param("customerId", customerId.toString()))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.length()").value(1))
                        .andExpect(jsonPath("$.data[0].status").value("SETTLED")));
    }

    @Test
    @WithMockUser(roles = "COMPLIANCE")
    void kycRequestedThenApprovedMaterializesAsApprovedInTheKycReport() throws Exception {
        UUID checkId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();

        publish("customer.kyc.requested", Map.of(
                "checkId", checkId,
                "customerId", customerId,
                "requestedAt", Instant.now()));
        publish("customer.kyc.approved", Map.of(
                "checkId", checkId,
                "customerId", customerId,
                "status", "APPROVED",
                "reason", "",
                "decidedBy", "compliance-officer-1",
                "decidedAt", Instant.now()));

        await().pollInSameThread().atMost(15, TimeUnit.SECONDS).untilAsserted(() ->
                mockMvc.perform(get("/api/v1/reports/kyc-checks").param("customerId", customerId.toString()))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.length()").value(1))
                        .andExpect(jsonPath("$.data[0].status").value("APPROVED")));
    }

    @Test
    @WithMockUser(roles = "COMPLIANCE")
    void amlRequestedThenFlaggedMaterializesAsCompletedWithAHitOutcome() throws Exception {
        UUID screeningId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();

        publish("customer.aml.requested", Map.of(
                "screeningId", screeningId,
                "customerId", customerId,
                "requestedAt", Instant.now()));
        publish("customer.aml.flagged", Map.of(
                "screeningId", screeningId,
                "customerId", customerId,
                "outcome", "HIT",
                "notes", "Sanctions list match",
                "completedAt", Instant.now()));

        await().pollInSameThread().atMost(15, TimeUnit.SECONDS).untilAsserted(() ->
                mockMvc.perform(get("/api/v1/reports/aml-screenings").param("customerId", customerId.toString()))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.length()").value(1))
                        .andExpect(jsonPath("$.data[0].status").value("COMPLETED"))
                        .andExpect(jsonPath("$.data[0].outcome").value("HIT")));
    }

    @Test
    @WithMockUser(roles = "OPERATIONS")
    void documentUploadedThenRejectedMaterializesAsRejectedInTheDocumentReport() throws Exception {
        UUID documentId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();

        publish("customer.document.uploaded", Map.of(
                "documentId", documentId,
                "customerId", customerId,
                "documentType", "PASSPORT",
                "uploadedAt", Instant.now()));
        publish("customer.document.rejected", Map.of(
                "documentId", documentId,
                "customerId", customerId,
                "status", "REJECTED",
                "notes", "Illegible scan",
                "reviewedAt", Instant.now()));

        await().pollInSameThread().atMost(15, TimeUnit.SECONDS).untilAsserted(() ->
                mockMvc.perform(get("/api/v1/reports/documents").param("customerId", customerId.toString()))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.length()").value(1))
                        .andExpect(jsonPath("$.data[0].status").value("REJECTED"))
                        .andExpect(jsonPath("$.data[0].notes").value("Illegible scan")));
    }

    @Test
    @WithMockUser(roles = "PORTFOLIO_MANAGER")
    void subscriptionReservedThenConfirmedMaterializesAsConfirmed() throws Exception {
        UUID subscriptionId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID portfolioId = UUID.randomUUID();

        publish("investment.subscription.reserved", Map.of(
                "subscriptionId", subscriptionId,
                "customerId", customerId,
                "portfolioId", portfolioId,
                "fundCode", "EQFND01",
                "quantity", new BigDecimal("50.0000"),
                "reservedAt", Instant.now()));
        publish("investment.subscription.confirmed", Map.of(
                "subscriptionId", subscriptionId,
                "customerId", customerId,
                "portfolioId", portfolioId,
                "fundCode", "EQFND01",
                "quantity", new BigDecimal("50.0000"),
                "confirmedAt", Instant.now()));

        await().pollInSameThread().atMost(15, TimeUnit.SECONDS).untilAsserted(() ->
                mockMvc.perform(get("/api/v1/reports/subscriptions").param("customerId", customerId.toString()))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.length()").value(1))
                        .andExpect(jsonPath("$.data[0].status").value("CONFIRMED")));
    }

    @Test
    @WithMockUser(roles = "PORTFOLIO_MANAGER")
    void subscriptionFailedWithoutAPriorReservationMaterializesAsFailed() throws Exception {
        UUID subscriptionId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();

        publish("investment.subscription.failed", Map.of(
                "subscriptionId", subscriptionId,
                "customerId", customerId,
                "failureReason", "KYC check rejected",
                "failedAt", Instant.now()));

        await().pollInSameThread().atMost(15, TimeUnit.SECONDS).untilAsserted(() ->
                mockMvc.perform(get("/api/v1/reports/subscriptions").param("customerId", customerId.toString()))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.length()").value(1))
                        .andExpect(jsonPath("$.data[0].status").value("FAILED"))
                        .andExpect(jsonPath("$.data[0].failureReason").value("KYC check rejected")));
    }

    @Test
    @WithMockUser(roles = "COMPLIANCE")
    void kycDecidedWithoutAPriorRequestMaterializesUsingTheFallbackView() throws Exception {
        UUID checkId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();

        publish("customer.kyc.rejected", Map.of(
                "checkId", checkId,
                "customerId", customerId,
                "status", "REJECTED",
                "reason", "Expired ID document",
                "decidedBy", "compliance-officer-1",
                "decidedAt", Instant.now()));

        await().pollInSameThread().atMost(15, TimeUnit.SECONDS).untilAsserted(() ->
                mockMvc.perform(get("/api/v1/reports/kyc-checks").param("customerId", customerId.toString()))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.length()").value(1))
                        .andExpect(jsonPath("$.data[0].status").value("REJECTED")));
    }

    @Test
    @WithMockUser(roles = "COMPLIANCE")
    void amlCompletedWithoutAPriorRequestMaterializesUsingTheFallbackView() throws Exception {
        UUID screeningId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();

        publish("customer.aml.cleared", Map.of(
                "screeningId", screeningId,
                "customerId", customerId,
                "outcome", "CLEAR",
                "notes", "",
                "completedAt", Instant.now()));

        await().pollInSameThread().atMost(15, TimeUnit.SECONDS).untilAsserted(() ->
                mockMvc.perform(get("/api/v1/reports/aml-screenings").param("customerId", customerId.toString()))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.length()").value(1))
                        .andExpect(jsonPath("$.data[0].outcome").value("CLEAR")));
    }

    @Test
    @WithMockUser(roles = "COMPLIANCE")
    void amlRequestedThenFailedMaterializesAsFailed() throws Exception {
        UUID screeningId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();

        publish("customer.aml.requested", Map.of(
                "screeningId", screeningId,
                "customerId", customerId,
                "requestedAt", Instant.now()));
        publish("customer.aml.failed", Map.of(
                "screeningId", screeningId,
                "customerId", customerId,
                "reason", "Provider timeout",
                "failedAt", Instant.now()));

        await().pollInSameThread().atMost(15, TimeUnit.SECONDS).untilAsserted(() ->
                mockMvc.perform(get("/api/v1/reports/aml-screenings").param("customerId", customerId.toString()))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.length()").value(1))
                        .andExpect(jsonPath("$.data[0].status").value("FAILED")));
    }

    @Test
    @WithMockUser(roles = "OPERATIONS")
    void documentReviewedWithoutAPriorUploadMaterializesUsingTheFallbackView() throws Exception {
        UUID documentId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();

        publish("customer.document.verified", Map.of(
                "documentId", documentId,
                "customerId", customerId,
                "status", "VERIFIED",
                "notes", "",
                "reviewedAt", Instant.now()));

        await().pollInSameThread().atMost(15, TimeUnit.SECONDS).untilAsserted(() ->
                mockMvc.perform(get("/api/v1/reports/documents").param("customerId", customerId.toString()))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.length()").value(1))
                        .andExpect(jsonPath("$.data[0].status").value("VERIFIED")));
    }

    @Test
    @WithMockUser(roles = "PORTFOLIO_MANAGER")
    void subscriptionReservedThenCancelledMaterializesAsCancelled() throws Exception {
        UUID subscriptionId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID portfolioId = UUID.randomUUID();

        publish("investment.subscription.reserved", Map.of(
                "subscriptionId", subscriptionId,
                "customerId", customerId,
                "portfolioId", portfolioId,
                "fundCode", "EQFND01",
                "quantity", new BigDecimal("25.0000"),
                "reservedAt", Instant.now()));
        publish("investment.subscription.cancelled", Map.of(
                "subscriptionId", subscriptionId,
                "customerId", customerId,
                "cancelledAt", Instant.now()));

        await().pollInSameThread().atMost(15, TimeUnit.SECONDS).untilAsserted(() ->
                mockMvc.perform(get("/api/v1/reports/subscriptions").param("customerId", customerId.toString()))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.length()").value(1))
                        .andExpect(jsonPath("$.data[0].status").value("CANCELLED")));
    }

    @Test
    @WithMockUser(roles = "PORTFOLIO_MANAGER")
    void subscriptionReservedThenTimedOutMaterializesAsTimedOut() throws Exception {
        UUID subscriptionId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID portfolioId = UUID.randomUUID();

        publish("investment.subscription.reserved", Map.of(
                "subscriptionId", subscriptionId,
                "customerId", customerId,
                "portfolioId", portfolioId,
                "fundCode", "EQFND01",
                "quantity", new BigDecimal("25.0000"),
                "reservedAt", Instant.now()));
        publish("investment.subscription.timed-out", Map.of(
                "subscriptionId", subscriptionId,
                "customerId", customerId,
                "timedOutAt", Instant.now()));

        await().pollInSameThread().atMost(15, TimeUnit.SECONDS).untilAsserted(() ->
                mockMvc.perform(get("/api/v1/reports/subscriptions").param("customerId", customerId.toString()))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.length()").value(1))
                        .andExpect(jsonPath("$.data[0].status").value("TIMED_OUT")));
    }

    @Test
    @WithMockUser(roles = "PORTFOLIO_MANAGER")
    void listingSubscriptionsWithoutACustomerIdFilterReturnsAllCustomers() throws Exception {
        UUID subscriptionId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();

        publish("investment.subscription.reserved", Map.of(
                "subscriptionId", subscriptionId,
                "customerId", customerId,
                "portfolioId", UUID.randomUUID(),
                "fundCode", "EQFND01",
                "quantity", new BigDecimal("10.0000"),
                "reservedAt", Instant.now()));

        await().pollInSameThread().atMost(15, TimeUnit.SECONDS).untilAsserted(() ->
                mockMvc.perform(get("/api/v1/reports/subscriptions"))
                        .andExpect(status().isOk())
                        .andExpect(content().string(org.hamcrest.Matchers.containsString(subscriptionId.toString()))));
    }

    @Test
    @WithMockUser(roles = "COMPLIANCE")
    void listingKycChecksWithoutACustomerIdFilterReturnsAllCustomers() throws Exception {
        UUID checkId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();

        publish("customer.kyc.requested", Map.of(
                "checkId", checkId,
                "customerId", customerId,
                "requestedAt", Instant.now()));

        await().pollInSameThread().atMost(15, TimeUnit.SECONDS).untilAsserted(() ->
                mockMvc.perform(get("/api/v1/reports/kyc-checks"))
                        .andExpect(status().isOk())
                        .andExpect(content().string(org.hamcrest.Matchers.containsString(checkId.toString()))));
    }

    @Test
    @WithMockUser(roles = "COMPLIANCE")
    void listingAmlScreeningsWithoutACustomerIdFilterReturnsAllCustomers() throws Exception {
        UUID screeningId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();

        publish("customer.aml.requested", Map.of(
                "screeningId", screeningId,
                "customerId", customerId,
                "requestedAt", Instant.now()));

        await().pollInSameThread().atMost(15, TimeUnit.SECONDS).untilAsserted(() ->
                mockMvc.perform(get("/api/v1/reports/aml-screenings"))
                        .andExpect(status().isOk())
                        .andExpect(content().string(org.hamcrest.Matchers.containsString(screeningId.toString()))));
    }

    @Test
    @WithMockUser(roles = "OPERATIONS")
    void listingDocumentsWithoutACustomerIdFilterReturnsAllCustomers() throws Exception {
        UUID documentId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();

        publish("customer.document.uploaded", Map.of(
                "documentId", documentId,
                "customerId", customerId,
                "documentType", "PASSPORT",
                "uploadedAt", Instant.now()));

        await().pollInSameThread().atMost(15, TimeUnit.SECONDS).untilAsserted(() ->
                mockMvc.perform(get("/api/v1/reports/documents"))
                        .andExpect(status().isOk())
                        .andExpect(content().string(org.hamcrest.Matchers.containsString(documentId.toString()))));
    }

    @Test
    void reportsRequireAStaffRole() throws Exception {
        mockMvc.perform(get("/api/v1/reports/funds")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "OPERATIONS")
    void returnsProblemDetailsForAFundWithNoNavData() throws Exception {
        mockMvc.perform(get("/api/v1/reports/funds/{fundCode}", "NOSUCHFUND"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RPT-4041"));
    }

    @Test
    @WithMockUser(roles = "OPERATIONS")
    void returnsProblemDetailsForAnUnknownPortfolio() throws Exception {
        mockMvc.perform(get("/api/v1/reports/portfolios/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RPT-4042"));
    }

    private void publish(String routingKey, Map<String, Object> payload) throws Exception {
        var envelope = new EventEnvelope<>(UUID.randomUUID(), routingKey, Instant.now(),
                "corr-" + UUID.randomUUID(), "test-producer", 1, payload);
        String body = objectMapper.writeValueAsString(envelope);

        rabbitTemplate.convertAndSend(EXCHANGE, routingKey, body, message -> {
            message.getMessageProperties().setMessageId(envelope.eventId().toString());
            message.getMessageProperties().setCorrelationId(envelope.correlationId());
            return message;
        });
    }
}
