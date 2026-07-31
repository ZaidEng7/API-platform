package com.company.customer.messaging;

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
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves the outbox → relay → RabbitMQ path is real, not just wired up:
 * creating/updating a customer over HTTP results in a customer.party.*
 * message actually reaching the domain-events exchange (guide §8.4, §22).
 * Audit Service's own integration test proves the consumer side against a
 * hand-published message; this is the matching producer-side proof, and
 * the first real producer common-messaging's outbox support has had.
 *
 * <p>The shared test queue (bound with a wildcard, since both event types
 * land here) is durable and never purged between test methods, so
 * {@link #awaitMatchingMessage} drains and discards non-matching messages
 * until it finds the one this test is looking for, rather than assuming
 * the very next message on the queue is always its own.
 */
@Import(CustomerEventPublishingIntegrationTest.TestQueueConfig.class)
class CustomerEventPublishingIntegrationTest extends AbstractMessagingIntegrationTest {

    private static final String TEST_QUEUE = "test.customer-party-events";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Test
    void creatingACustomerPublishesCustomerPartyCreatedEvent() throws Exception {
        String requestBody = """
                {"fullName": "Ada Wong", "email": "ada.wong.events@example.com"}
                """;

        mockMvc.perform(post("/api/v1/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated());

        Message message = awaitMatchingMessage("ada.wong.events@example.com");
        assertThat(message.getMessageProperties().getReceivedRoutingKey()).isEqualTo("customer.party.created");
        assertThat(new String(message.getBody(), StandardCharsets.UTF_8))
                .contains("\"eventType\":\"customer.party.created\"")
                .contains("\"producer\":\"customer-service\"");
    }

    @Test
    void updatingACustomerPublishesCustomerPartyUpdatedEvent() throws Exception {
        String createBody = """
                {"fullName": "Barbara Liskov", "email": "barbara.events@example.com"}
                """;
        String location = mockMvc.perform(post("/api/v1/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
        awaitMatchingMessage("barbara.events@example.com"); // drain the created event first

        String updateBody = """
                {"fullName": "Barbara H. Liskov", "phone": "+1-555-0177"}
                """;
        mockMvc.perform(put(location)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk());

        Message message = awaitMatchingMessage("Barbara H. Liskov");
        assertThat(message.getMessageProperties().getReceivedRoutingKey()).isEqualTo("customer.party.updated");
        assertThat(new String(message.getBody(), StandardCharsets.UTF_8))
                .contains("\"eventType\":\"customer.party.updated\"")
                .contains("+1-555-0177");
    }

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
        Queue testCustomerPartyEventsQueue() {
            return QueueBuilder.durable(TEST_QUEUE).build();
        }

        @Bean
        Binding testCustomerPartyEventsBinding(Queue testCustomerPartyEventsQueue,
                                                 TopicExchange domainEventsExchange) {
            return BindingBuilder.bind(testCustomerPartyEventsQueue).to(domainEventsExchange)
                    .with("customer.party.*");
        }
    }
}
