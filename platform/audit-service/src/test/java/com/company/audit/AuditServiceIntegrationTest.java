package com.company.audit;

import com.company.platform.test.AbstractMessagingIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Real Postgres + RabbitMQ (guide §20). No producer exists yet in this
 * codebase, so this test plays that role directly: publishes onto the
 * actual domain-events exchange and asserts the full path — consume,
 * persist, dedupe on redelivery, and role-restricted read — all work.
 */
class AuditServiceIntegrationTest extends AbstractMessagingIntegrationTest {

    private static final String EXCHANGE = "domain-events";

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser(roles = "AUDITOR")
    void consumesPersistsDedupesAndServesAuditEvent() {
        String eventId = UUID.randomUUID().toString();
        String routingKey = "customer.kyc.approved";
        String payload = "{\"customerId\":\"abc-123\"}";

        publish(eventId, routingKey, payload);
        publish(eventId, routingKey, payload); // redelivery — must be deduped, not double-recorded

        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            var result = mockMvc.perform(get("/api/v1/audit-events").param("eventType", routingKey))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(1))
                    .andExpect(jsonPath("$.data[0].sourceEventId").value(eventId))
                    .andExpect(jsonPath("$.data[0].eventType").value(routingKey))
                    .andExpect(jsonPath("$.data[0].rawPayload").value(payload));
        });
    }

    @Test
    void listRequiresAuditorOrComplianceRole() throws Exception {
        mockMvc.perform(get("/api/v1/audit-events"))
                .andExpect(status().isForbidden());
    }

    private void publish(String messageId, String routingKey, String payload) {
        rabbitTemplate.convertAndSend(EXCHANGE, routingKey, payload, message -> {
            message.getMessageProperties().setMessageId(messageId);
            message.getMessageProperties().setCorrelationId("corr-" + messageId);
            return message;
        });
    }
}
