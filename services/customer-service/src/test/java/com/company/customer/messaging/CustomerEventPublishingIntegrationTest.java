package com.company.customer.messaging;

import com.company.platform.test.AbstractMessagingIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves the outbox → relay → RabbitMQ path is real, not just wired up:
 * creating a customer over HTTP results in a customer.party.created
 * message actually reaching the domain-events exchange (guide §8.4, §22).
 * Audit Service's own integration test proves the consumer side against a
 * hand-published message; this is the matching producer-side proof, and
 * the first real producer common-messaging's outbox support has had.
 */
@Import(CustomerEventPublishingIntegrationTest.TestQueueConfig.class)
class CustomerEventPublishingIntegrationTest extends AbstractMessagingIntegrationTest {

    private static final String TEST_QUEUE = "test.customer-party-created";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Test
    void creatingACustomerPublishesCustomerPartyCreatedEvent() throws Exception {
        String requestBody = """
                {"fullName": "Grace Hopper", "email": "grace@example.com"}
                """;

        mockMvc.perform(post("/api/v1/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated());

        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            var message = rabbitTemplate.receive(TEST_QUEUE);
            assertThat(message).isNotNull();
            assertThat(message.getMessageProperties().getReceivedRoutingKey())
                    .isEqualTo("customer.party.created");
            assertThat(new String(message.getBody(), StandardCharsets.UTF_8))
                    .contains("grace@example.com")
                    .contains("\"eventType\":\"customer.party.created\"")
                    .contains("\"producer\":\"customer-service\"");
        });
    }

    @TestConfiguration
    static class TestQueueConfig {

        @Bean
        Queue testCustomerPartyCreatedQueue() {
            return QueueBuilder.durable(TEST_QUEUE).build();
        }

        @Bean
        Binding testCustomerPartyCreatedBinding(Queue testCustomerPartyCreatedQueue,
                                                  TopicExchange domainEventsExchange) {
            return BindingBuilder.bind(testCustomerPartyCreatedQueue).to(domainEventsExchange)
                    .with("customer.party.created");
        }
    }
}
