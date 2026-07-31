package com.company.investment.messaging;

import com.company.platform.test.AbstractMessagingIntegrationTest;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves the outbox → relay → RabbitMQ path is real for the saga's event
 * types (guide §8.4, §22), including {@code investment.subscription.confirmed}
 * — the exact event name the guide's own §22 naming example uses.
 */
@Import(SubscriptionEventPublishingIntegrationTest.TestQueueConfig.class)
@WithMockUser(roles = "OPERATIONS")
class SubscriptionEventPublishingIntegrationTest extends AbstractMessagingIntegrationTest {

    private static final int WIREMOCK_PORT = 9990;
    private static final String TEST_QUEUE = "test.investment-events";

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

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @BeforeEach
    void resetStubs() {
        wireMock.resetAll();
    }

    @Test
    void reservingASubscriptionPublishesInvestmentSubscriptionReservedEvent() throws Exception {
        UUID customerId = UUID.randomUUID();
        stubHappyPathChecks(customerId);

        mockMvc.perform(post("/api/v1/subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .content(requestBody(customerId, UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isCreated());

        Message message = awaitMatchingMessage(customerId.toString());
        assertThat(message.getMessageProperties().getReceivedRoutingKey())
                .isEqualTo("investment.subscription.reserved");
        assertThat(new String(message.getBody(), StandardCharsets.UTF_8))
                .contains("\"eventType\":\"investment.subscription.reserved\"")
                .contains("\"producer\":\"investment-service\"");
    }

    @Test
    void failingComplianceChecksPublishesInvestmentSubscriptionFailedEvent() throws Exception {
        UUID customerId = UUID.randomUUID();
        stubFor(WireMock.get(urlPathMatching("/api/v1/customers/" + customerId))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"success\": true, \"data\": {\"id\": \"" + customerId + "\"}}")));
        stubFor(WireMock.get(urlPathMatching("/api/v1/kyc-checks.*"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"success": true, "data": [{"status": "REJECTED"}]}
                                """)));
        stubFor(WireMock.get(urlPathMatching("/api/v1/aml/screenings.*"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"success": true, "data": [{"status": "COMPLETED", "outcome": "CLEAR"}]}
                                """)));

        mockMvc.perform(post("/api/v1/subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .content(requestBody(customerId, UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isCreated());

        Message message = awaitMatchingMessage(customerId.toString());
        assertThat(message.getMessageProperties().getReceivedRoutingKey())
                .isEqualTo("investment.subscription.failed");
        assertThat(new String(message.getBody(), StandardCharsets.UTF_8))
                .contains("\"eventType\":\"investment.subscription.failed\"")
                .contains("KYC not approved");
    }

    @Test
    void confirmingPaymentPublishesInvestmentSubscriptionConfirmedEvent() throws Exception {
        UUID customerId = UUID.randomUUID();
        stubHappyPathChecks(customerId);
        String location = mockMvc.perform(post("/api/v1/subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .content(requestBody(customerId, UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
        awaitMatchingMessage(customerId.toString()); // drain the reserved event first
        stubFor(WireMock.post(urlPathMatching("/api/v1/portfolios/.*/positions")).willReturn(aResponse().withStatus(201)));

        mockMvc.perform(post(location + "/confirm-payment")).andExpect(status().isOk());

        Message message = awaitMatchingMessage("investment.subscription.confirmed");
        assertThat(message.getMessageProperties().getReceivedRoutingKey())
                .isEqualTo("investment.subscription.confirmed");
        assertThat(new String(message.getBody(), StandardCharsets.UTF_8))
                .contains("\"eventType\":\"investment.subscription.confirmed\"");
    }

    @Test
    void cancellingPublishesInvestmentSubscriptionCancelledEvent() throws Exception {
        UUID customerId = UUID.randomUUID();
        stubHappyPathChecks(customerId);
        String location = mockMvc.perform(post("/api/v1/subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .content(requestBody(customerId, UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
        awaitMatchingMessage(customerId.toString()); // drain the reserved event first

        mockMvc.perform(post(location + "/cancel")).andExpect(status().isOk());

        Message message = awaitMatchingMessage("investment.subscription.cancelled");
        assertThat(message.getMessageProperties().getReceivedRoutingKey())
                .isEqualTo("investment.subscription.cancelled");
        assertThat(new String(message.getBody(), StandardCharsets.UTF_8))
                .contains("\"eventType\":\"investment.subscription.cancelled\"");
    }

    private String requestBody(UUID customerId, UUID ownerId, UUID portfolioId) {
        return """
                {"customerId": "%s", "ownerId": "%s", "portfolioId": "%s", "fundCode": "EQFND01", "quantity": 100}
                """.formatted(customerId, ownerId, portfolioId);
    }

    private void stubHappyPathChecks(UUID customerId) {
        stubFor(WireMock.get(urlPathMatching("/api/v1/customers/" + customerId))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"success\": true, \"data\": {\"id\": \"" + customerId + "\"}}")));
        stubFor(WireMock.get(urlPathMatching("/api/v1/kyc-checks.*"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"success": true, "data": [{"status": "APPROVED"}]}
                                """)));
        stubFor(WireMock.get(urlPathMatching("/api/v1/aml/screenings.*"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"success": true, "data": [{"status": "COMPLETED", "outcome": "CLEAR"}]}
                                """)));
    }

    /**
     * The shared test queue is durable and never purged between test
     * methods (see the equivalent helper in the other Phase 5 services'
     * own event-publishing integration tests), so this drains and
     * discards non-matching messages until it finds the one this test
     * wants.
     */
    private Message awaitMatchingMessage(String expectedBodyFragment) {
        var found = new Message[1];
        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            Message message = rabbitTemplate.receive(TEST_QUEUE);
            assertThat(message).as("no more messages arrived on %s before finding one containing '%s'",
                    TEST_QUEUE, expectedBodyFragment).isNotNull();
            if (new String(message.getBody(), StandardCharsets.UTF_8).contains(expectedBodyFragment)) {
                found[0] = message;
            } else {
                throw new AssertionError("message did not match, discarding and retrying");
            }
        });
        return found[0];
    }

    @TestConfiguration
    static class TestQueueConfig {

        @Bean
        Queue testInvestmentEventsQueue() {
            return QueueBuilder.durable(TEST_QUEUE).build();
        }

        @Bean
        Binding testInvestmentEventsBinding(Queue testInvestmentEventsQueue, TopicExchange domainEventsExchange) {
            return BindingBuilder.bind(testInvestmentEventsQueue).to(domainEventsExchange).with("investment.#");
        }
    }
}
