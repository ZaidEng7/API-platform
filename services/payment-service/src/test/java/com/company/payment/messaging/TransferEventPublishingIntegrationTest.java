package com.company.payment.messaging;

import com.company.platform.test.AbstractMessagingIntegrationTest;
import org.junit.jupiter.api.Test;
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
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves the outbox → relay → RabbitMQ path is real for the three transfer
 * event types (guide §8.4, §22), including {@code payment.transfer.settled}
 * — the exact event name the guide's own §22 naming example uses.
 */
@Import(TransferEventPublishingIntegrationTest.TestQueueConfig.class)
class TransferEventPublishingIntegrationTest extends AbstractMessagingIntegrationTest {

    private static final String TEST_QUEUE = "test.payment-events";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Test
    void requestingATransferPublishesPaymentTransferRequestedEvent() throws Exception {
        UUID customerId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .content(requestBody(customerId, UUID.randomUUID()))
                        .with(user("ops").roles("OPERATIONS")))
                .andExpect(status().isAccepted());

        Message message = awaitMatchingMessage(customerId.toString());
        assertThat(message.getMessageProperties().getReceivedRoutingKey()).isEqualTo("payment.transfer.requested");
        assertThat(new String(message.getBody(), StandardCharsets.UTF_8))
                .contains("\"eventType\":\"payment.transfer.requested\"")
                .contains("\"producer\":\"payment-service\"");
    }

    @Test
    void settlingATransferPublishesPaymentTransferSettledEvent() throws Exception {
        UUID customerId = UUID.randomUUID();
        String location = mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .content(requestBody(customerId, UUID.randomUUID()))
                        .with(user("ops").roles("OPERATIONS")))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getHeader("Location");
        awaitMatchingMessage(customerId.toString()); // drain the requested event first

        mockMvc.perform(post(location + "/settle").with(user("ops").roles("OPERATIONS")))
                .andExpect(status().isOk());

        Message message = awaitMatchingMessage("payment.transfer.settled");
        assertThat(message.getMessageProperties().getReceivedRoutingKey()).isEqualTo("payment.transfer.settled");
        assertThat(new String(message.getBody(), StandardCharsets.UTF_8))
                .contains("\"eventType\":\"payment.transfer.settled\"");
    }

    @Test
    void failingATransferPublishesPaymentTransferFailedEvent() throws Exception {
        UUID customerId = UUID.randomUUID();
        String location = mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .content(requestBody(customerId, UUID.randomUUID()))
                        .with(user("ops").roles("OPERATIONS")))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getHeader("Location");
        awaitMatchingMessage(customerId.toString()); // drain the requested event first

        mockMvc.perform(post(location + "/fail")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason": "Card declined by issuer"}
                                """)
                        .with(user("ops").roles("OPERATIONS")))
                .andExpect(status().isOk());

        Message message = awaitMatchingMessage("Card declined by issuer");
        assertThat(message.getMessageProperties().getReceivedRoutingKey()).isEqualTo("payment.transfer.failed");
        assertThat(new String(message.getBody(), StandardCharsets.UTF_8))
                .contains("\"eventType\":\"payment.transfer.failed\"");
    }

    private String requestBody(UUID customerId, UUID ownerId) {
        return """
                {"customerId": "%s", "ownerId": "%s", "amount": 100.00, "currency": "USD",
                 "paymentMethodToken": "tok_visa_1234", "reference": "subscription-ref"}
                """.formatted(customerId, ownerId);
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
        Queue testPaymentEventsQueue() {
            return QueueBuilder.durable(TEST_QUEUE).build();
        }

        @Bean
        Binding testPaymentEventsBinding(Queue testPaymentEventsQueue, TopicExchange domainEventsExchange) {
            return BindingBuilder.bind(testPaymentEventsQueue).to(domainEventsExchange).with("payment.#");
        }
    }
}
