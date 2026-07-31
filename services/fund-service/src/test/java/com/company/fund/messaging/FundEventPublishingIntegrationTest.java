package com.company.fund.messaging;

import com.company.platform.test.AbstractMessagingIntegrationTest;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves the outbox → relay → RabbitMQ path is real for both fund event
 * types (guide §8.4, §22): {@code fund.definition.registered} on
 * registration, {@code fund.nav.updated} on a NAV refresh (which itself
 * calls the WireMock-stubbed fund-mgmt-adapter — see
 * FundNavRefreshIntegrationTest for that call's own dedicated coverage).
 */
@Import(FundEventPublishingIntegrationTest.TestQueueConfig.class)
class FundEventPublishingIntegrationTest extends AbstractMessagingIntegrationTest {

    private static final int WIREMOCK_PORT = 9997;
    private static final String TEST_QUEUE = "test.fund-events";

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(WireMockConfiguration.wireMockConfig().port(WIREMOCK_PORT))
            .configureStaticDsl(true)
            .build();

    @DynamicPropertySource
    static void fundMgmtAdapterBaseUrl(DynamicPropertyRegistry registry) {
        registry.add("fund-mgmt-adapter.base-url", () -> "http://localhost:" + WIREMOCK_PORT);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Test
    void registeringAFundPublishesFundDefinitionRegisteredEvent() throws Exception {
        mockMvc.perform(post("/api/v1/funds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fundCode": "EQFND20", "name": "Global Equity Fund", "currency": "USD"}
                                """)
                        .with(user("ops").roles("OPERATIONS")))
                .andExpect(status().isCreated());

        Message message = awaitMatchingMessage("EQFND20");
        assertThat(message.getMessageProperties().getReceivedRoutingKey()).isEqualTo("fund.definition.registered");
        assertThat(new String(message.getBody(), StandardCharsets.UTF_8))
                .contains("\"eventType\":\"fund.definition.registered\"")
                .contains("\"producer\":\"fund-service\"");
    }

    @Test
    void refreshingNavPublishesFundNavUpdatedEvent() throws Exception {
        mockMvc.perform(post("/api/v1/funds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fundCode": "EQFND21", "name": "Bond Fund", "currency": "USD"}
                                """)
                        .with(user("ops").roles("OPERATIONS")))
                .andExpect(status().isCreated());
        awaitMatchingMessage("EQFND21"); // drain the registered event first

        stubFor(get(urlPathMatching("/api/v1/funds/EQFND21/nav"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"success": true, "data": {"fundCode": "EQFND21", "navPerShare": 99.5000, "asOfDate": "2026-07-30"}}
                                """)));

        mockMvc.perform(post("/api/v1/funds/{fundCode}/nav/refresh", "EQFND21")
                        .with(user("ops").roles("OPERATIONS")))
                .andExpect(status().isOk());

        Message message = awaitMatchingMessage("99.5");
        assertThat(message.getMessageProperties().getReceivedRoutingKey()).isEqualTo("fund.nav.updated");
        assertThat(new String(message.getBody(), StandardCharsets.UTF_8))
                .contains("\"eventType\":\"fund.nav.updated\"")
                .contains("\"fundCode\":\"EQFND21\"");
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
        Queue testFundEventsQueue() {
            return QueueBuilder.durable(TEST_QUEUE).build();
        }

        @Bean
        Binding testFundEventsBinding(Queue testFundEventsQueue, TopicExchange domainEventsExchange) {
            return BindingBuilder.bind(testFundEventsQueue).to(domainEventsExchange).with("fund.#");
        }
    }
}
