package com.company.portfolio.messaging;

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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves the outbox → relay → RabbitMQ path is real for both portfolio
 * event types (guide §8.4, §22): {@code portfolio.account.opened} on
 * opening, {@code portfolio.position.recorded} on recording a position.
 */
@Import(PortfolioEventPublishingIntegrationTest.TestQueueConfig.class)
@WithMockUser(roles = "OPERATIONS")
class PortfolioEventPublishingIntegrationTest extends AbstractMessagingIntegrationTest {

    private static final String TEST_QUEUE = "test.portfolio-events";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Test
    void openingAPortfolioPublishesPortfolioAccountOpenedEvent() throws Exception {
        UUID ownerId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/portfolios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId": "%s", "ownerId": "%s", "name": "Retirement Account", "currency": "USD"}
                                """.formatted(UUID.randomUUID(), ownerId)))
                .andExpect(status().isCreated());

        Message message = awaitMatchingMessage(ownerId.toString());
        assertThat(message.getMessageProperties().getReceivedRoutingKey()).isEqualTo("portfolio.account.opened");
        assertThat(new String(message.getBody(), StandardCharsets.UTF_8))
                .contains("\"eventType\":\"portfolio.account.opened\"")
                .contains("\"producer\":\"portfolio-service\"");
    }

    @Test
    void recordingAPositionPublishesPortfolioPositionRecordedEvent() throws Exception {
        String location = mockMvc.perform(post("/api/v1/portfolios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId": "%s", "ownerId": "%s", "name": "Taxable Brokerage", "currency": "USD"}
                                """.formatted(UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
        awaitMatchingMessage(location.substring(location.lastIndexOf('/') + 1)); // drain the opened event first

        mockMvc.perform(post(location + "/positions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fundCode": "EQFND01", "quantity": 42}
                                """))
                .andExpect(status().isCreated());

        Message message = awaitMatchingMessage("EQFND01");
        assertThat(message.getMessageProperties().getReceivedRoutingKey()).isEqualTo("portfolio.position.recorded");
        assertThat(new String(message.getBody(), StandardCharsets.UTF_8))
                .contains("\"eventType\":\"portfolio.position.recorded\"")
                .contains("\"fundCode\":\"EQFND01\"");
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
        Queue testPortfolioEventsQueue() {
            return QueueBuilder.durable(TEST_QUEUE).build();
        }

        @Bean
        Binding testPortfolioEventsBinding(Queue testPortfolioEventsQueue, TopicExchange domainEventsExchange) {
            return BindingBuilder.bind(testPortfolioEventsQueue).to(domainEventsExchange).with("portfolio.#");
        }
    }
}
