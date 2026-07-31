package com.company.kyc.messaging;

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
 * Proves the outbox → relay → RabbitMQ path is real for all three KYC
 * event types (guide §8.4, §22) — {@code customer.kyc.requested} on request,
 * {@code customer.kyc.approved}/{@code customer.kyc.rejected} on decision.
 * {@code customer.kyc.approved} is the guide's own §22 naming example, so
 * getting this exact routing key right end to end matters more here than
 * for most other events in this platform.
 */
@Import(KycEventPublishingIntegrationTest.TestQueueConfig.class)
class KycEventPublishingIntegrationTest extends AbstractMessagingIntegrationTest {

    private static final String TEST_QUEUE = "test.kyc-events";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Test
    void requestingACheckPublishesCustomerKycRequestedEvent() throws Exception {
        UUID customerId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/kyc-checks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId": "%s"}
                                """.formatted(customerId))
                        .with(user("ops").roles("OPERATIONS")))
                .andExpect(status().isCreated());

        Message message = awaitMatchingMessage(customerId.toString());
        assertThat(message.getMessageProperties().getReceivedRoutingKey()).isEqualTo("customer.kyc.requested");
        assertThat(new String(message.getBody(), StandardCharsets.UTF_8))
                .contains("\"eventType\":\"customer.kyc.requested\"")
                .contains("\"producer\":\"kyc-service\"");
    }

    @Test
    void approvingACheckPublishesCustomerKycApprovedEvent() throws Exception {
        UUID customerId = UUID.randomUUID();
        String location = mockMvc.perform(post("/api/v1/kyc-checks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId": "%s"}
                                """.formatted(customerId))
                        .with(user("ops").roles("OPERATIONS")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
        awaitMatchingMessage(customerId.toString()); // drain the requested event first

        mockMvc.perform(post(location + "/decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"outcome": "APPROVED", "reason": "Documents verified"}
                                """)
                        .with(user("compliance-officer").roles("COMPLIANCE")))
                .andExpect(status().isOk());

        Message message = awaitMatchingMessage("Documents verified");
        assertThat(message.getMessageProperties().getReceivedRoutingKey()).isEqualTo("customer.kyc.approved");
        assertThat(new String(message.getBody(), StandardCharsets.UTF_8))
                .contains("\"eventType\":\"customer.kyc.approved\"")
                .contains("\"status\":\"APPROVED\"");
    }

    @Test
    void rejectingACheckPublishesCustomerKycRejectedEvent() throws Exception {
        UUID customerId = UUID.randomUUID();
        String location = mockMvc.perform(post("/api/v1/kyc-checks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId": "%s"}
                                """.formatted(customerId))
                        .with(user("ops").roles("OPERATIONS")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
        awaitMatchingMessage(customerId.toString()); // drain the requested event first

        mockMvc.perform(post(location + "/decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"outcome": "REJECTED", "reason": "Sanctions list match"}
                                """)
                        .with(user("compliance-officer").roles("COMPLIANCE")))
                .andExpect(status().isOk());

        Message message = awaitMatchingMessage("Sanctions list match");
        assertThat(message.getMessageProperties().getReceivedRoutingKey()).isEqualTo("customer.kyc.rejected");
        assertThat(new String(message.getBody(), StandardCharsets.UTF_8))
                .contains("\"eventType\":\"customer.kyc.rejected\"")
                .contains("\"status\":\"REJECTED\"");
    }

    /**
     * The shared test queue is durable and never purged between test
     * methods (see the equivalent helper in customer-service's
     * CustomerEventPublishingIntegrationTest), so this drains and discards
     * non-matching messages until it finds the one this test wants.
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
        Queue testKycEventsQueue() {
            return QueueBuilder.durable(TEST_QUEUE).build();
        }

        @Bean
        Binding testKycEventsBinding(Queue testKycEventsQueue, TopicExchange domainEventsExchange) {
            return BindingBuilder.bind(testKycEventsQueue).to(domainEventsExchange).with("customer.kyc.*");
        }
    }
}
