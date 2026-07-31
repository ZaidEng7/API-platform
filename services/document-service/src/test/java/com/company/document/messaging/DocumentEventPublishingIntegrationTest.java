package com.company.document.messaging;

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
 * Proves the outbox → relay → RabbitMQ path is real for all three document
 * event types (guide §8.4, §22): {@code customer.document.uploaded} on
 * upload, {@code customer.document.verified}/{@code customer.document.rejected}
 * on review.
 */
@Import(DocumentEventPublishingIntegrationTest.TestQueueConfig.class)
class DocumentEventPublishingIntegrationTest extends AbstractMessagingIntegrationTest {

    private static final String TEST_QUEUE = "test.document-events";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Test
    void uploadingADocumentPublishesCustomerDocumentUploadedEvent() throws Exception {
        UUID customerId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId": "%s", "documentType": "PASSPORT", "storageReference": "dms://ref-1"}
                                """.formatted(customerId))
                        .with(user("ops").roles("OPERATIONS")))
                .andExpect(status().isCreated());

        Message message = awaitMatchingMessage(customerId.toString());
        assertThat(message.getMessageProperties().getReceivedRoutingKey()).isEqualTo("customer.document.uploaded");
        assertThat(new String(message.getBody(), StandardCharsets.UTF_8))
                .contains("\"eventType\":\"customer.document.uploaded\"")
                .contains("\"producer\":\"document-service\"")
                .doesNotContain("dms://ref-1"); // storageReference isn't part of the uploaded-event payload
    }

    @Test
    void verifyingADocumentPublishesCustomerDocumentVerifiedEvent() throws Exception {
        UUID customerId = UUID.randomUUID();
        String location = uploadDocument(customerId);
        awaitMatchingMessage(customerId.toString()); // drain the uploaded event first

        mockMvc.perform(post(location + "/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"notes": "Clear scan, matches name on file"}
                                """)
                        .with(user("compliance-officer").roles("COMPLIANCE")))
                .andExpect(status().isOk());

        Message message = awaitMatchingMessage("Clear scan, matches name on file");
        assertThat(message.getMessageProperties().getReceivedRoutingKey()).isEqualTo("customer.document.verified");
        assertThat(new String(message.getBody(), StandardCharsets.UTF_8))
                .contains("\"eventType\":\"customer.document.verified\"")
                .contains("\"status\":\"VERIFIED\"");
    }

    @Test
    void rejectingADocumentPublishesCustomerDocumentRejectedEvent() throws Exception {
        UUID customerId = UUID.randomUUID();
        String location = uploadDocument(customerId);
        awaitMatchingMessage(customerId.toString()); // drain the uploaded event first

        mockMvc.perform(post(location + "/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"notes": "Blurry, illegible expiry date"}
                                """)
                        .with(user("compliance-officer").roles("COMPLIANCE")))
                .andExpect(status().isOk());

        Message message = awaitMatchingMessage("Blurry, illegible expiry date");
        assertThat(message.getMessageProperties().getReceivedRoutingKey()).isEqualTo("customer.document.rejected");
        assertThat(new String(message.getBody(), StandardCharsets.UTF_8))
                .contains("\"eventType\":\"customer.document.rejected\"")
                .contains("\"status\":\"REJECTED\"");
    }

    private String uploadDocument(UUID customerId) throws Exception {
        return mockMvc.perform(post("/api/v1/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId": "%s", "documentType": "PASSPORT", "storageReference": "dms://ref-1"}
                                """.formatted(customerId))
                        .with(user("ops").roles("OPERATIONS")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
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
        Queue testDocumentEventsQueue() {
            return QueueBuilder.durable(TEST_QUEUE).build();
        }

        @Bean
        Binding testDocumentEventsBinding(Queue testDocumentEventsQueue, TopicExchange domainEventsExchange) {
            return BindingBuilder.bind(testDocumentEventsQueue).to(domainEventsExchange).with("customer.document.*");
        }
    }
}
