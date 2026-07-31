package com.company.aml.messaging;

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
 * Proves the outbox → relay → RabbitMQ path is real for all four AML event
 * types (guide §8.4, §22): {@code customer.aml.requested} on request,
 * {@code customer.aml.cleared}/{@code customer.aml.flagged} on a compliance
 * result, {@code customer.aml.failed} on a technical failure.
 */
@Import(AmlEventPublishingIntegrationTest.TestQueueConfig.class)
class AmlEventPublishingIntegrationTest extends AbstractMessagingIntegrationTest {

    private static final String TEST_QUEUE = "test.aml-events";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Test
    void requestingAScreeningPublishesCustomerAmlRequestedEvent() throws Exception {
        UUID customerId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/aml/screenings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId": "%s"}
                                """.formatted(customerId))
                        .with(user("ops").roles("OPERATIONS")))
                .andExpect(status().isAccepted());

        Message message = awaitMatchingMessage(customerId.toString());
        assertThat(message.getMessageProperties().getReceivedRoutingKey()).isEqualTo("customer.aml.requested");
        assertThat(new String(message.getBody(), StandardCharsets.UTF_8))
                .contains("\"eventType\":\"customer.aml.requested\"")
                .contains("\"producer\":\"aml-service\"");
    }

    @Test
    void clearingAScreeningPublishesCustomerAmlClearedEvent() throws Exception {
        UUID customerId = UUID.randomUUID();
        String location = requestScreening(customerId);
        awaitMatchingMessage(customerId.toString()); // drain the requested event first

        mockMvc.perform(post(location + "/result")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"outcome": "CLEAR", "notes": "No watchlist match"}
                                """)
                        .with(user("compliance-officer").roles("COMPLIANCE")))
                .andExpect(status().isOk());

        Message message = awaitMatchingMessage("No watchlist match");
        assertThat(message.getMessageProperties().getReceivedRoutingKey()).isEqualTo("customer.aml.cleared");
        assertThat(new String(message.getBody(), StandardCharsets.UTF_8))
                .contains("\"eventType\":\"customer.aml.cleared\"")
                .contains("\"outcome\":\"CLEAR\"");
    }

    @Test
    void flaggingAScreeningPublishesCustomerAmlFlaggedEvent() throws Exception {
        UUID customerId = UUID.randomUUID();
        String location = requestScreening(customerId);
        awaitMatchingMessage(customerId.toString()); // drain the requested event first

        mockMvc.perform(post(location + "/result")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"outcome": "HIT", "notes": "Possible sanctions list match"}
                                """)
                        .with(user("compliance-officer").roles("COMPLIANCE")))
                .andExpect(status().isOk());

        Message message = awaitMatchingMessage("Possible sanctions list match");
        assertThat(message.getMessageProperties().getReceivedRoutingKey()).isEqualTo("customer.aml.flagged");
        assertThat(new String(message.getBody(), StandardCharsets.UTF_8))
                .contains("\"eventType\":\"customer.aml.flagged\"")
                .contains("\"outcome\":\"HIT\"");
    }

    @Test
    void failingAScreeningPublishesCustomerAmlFailedEvent() throws Exception {
        UUID customerId = UUID.randomUUID();
        String location = requestScreening(customerId);
        awaitMatchingMessage(customerId.toString()); // drain the requested event first

        mockMvc.perform(post(location + "/fail")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason": "Watchlist vendor adapter timeout"}
                                """)
                        .with(user("ops").roles("OPERATIONS")))
                .andExpect(status().isOk());

        Message message = awaitMatchingMessage("Watchlist vendor adapter timeout");
        assertThat(message.getMessageProperties().getReceivedRoutingKey()).isEqualTo("customer.aml.failed");
        assertThat(new String(message.getBody(), StandardCharsets.UTF_8))
                .contains("\"eventType\":\"customer.aml.failed\"");
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

    /**
     * The shared test queue is durable and never purged between test
     * methods (see the equivalent helper in customer-service's and
     * kyc-service's own event-publishing integration tests), so this
     * drains and discards non-matching messages until it finds the one
     * this test wants.
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
        Queue testAmlEventsQueue() {
            return QueueBuilder.durable(TEST_QUEUE).build();
        }

        @Bean
        Binding testAmlEventsBinding(Queue testAmlEventsQueue, TopicExchange domainEventsExchange) {
            return BindingBuilder.bind(testAmlEventsQueue).to(domainEventsExchange).with("customer.aml.*");
        }
    }
}
