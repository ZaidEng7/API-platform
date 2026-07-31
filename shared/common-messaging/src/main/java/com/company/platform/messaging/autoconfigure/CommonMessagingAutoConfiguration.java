package com.company.platform.messaging.autoconfigure;

import com.company.platform.messaging.idempotent.IdempotencyGuard;
import com.company.platform.messaging.idempotent.ProcessedEvent;
import com.company.platform.messaging.outbox.OutboxEvent;
import com.company.platform.messaging.outbox.OutboxEventStore;
import com.company.platform.messaging.outbox.OutboxRelayPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Self-registers outbox and idempotent-consumer support for any service
 * that depends on this module and has RabbitMQ on the classpath (guide
 * §19: drop-in shared plumbing). {@code @EntityScan} is additive across
 * modules — safe to combine with the consuming service's own entities.
 */
@AutoConfiguration
@EntityScan(basePackageClasses = {OutboxEvent.class, ProcessedEvent.class})
@ConditionalOnClass(RabbitTemplate.class)
@EnableScheduling
public class CommonMessagingAutoConfiguration {

    // Spring Boot 4's own Jackson autoconfiguration defaults to Jackson 3
    // (tools.jackson) and no longer provides a com.fasterxml.jackson (Jackson
    // 2) ObjectMapper bean by default. OutboxEventStore is intentionally on
    // Jackson 2 (matches the rest of this module), so it needs its own
    // instance rather than assuming one is auto-configured.
    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
                .findAndRegisterModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Bean
    @ConditionalOnMissingBean
    public OutboxEventStore outboxEventStore(ObjectMapper objectMapper) {
        return new OutboxEventStore(objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public IdempotencyGuard idempotencyGuard() {
        return new IdempotencyGuard();
    }

    @Bean
    @ConditionalOnMissingBean(name = "domainEventsExchange")
    public TopicExchange domainEventsExchange(@Value("${platform.messaging.exchange:domain-events}") String exchange) {
        return new TopicExchange(exchange, true, false);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "platform.messaging.outbox-relay", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public OutboxRelayPublisher outboxRelayPublisher(
            OutboxEventStore outboxEventStore,
            RabbitTemplate rabbitTemplate,
            @Value("${platform.messaging.exchange:domain-events}") String exchange,
            @Value("${platform.messaging.outbox-relay.batch-size:50}") int batchSize,
            @Value("${platform.messaging.outbox-relay.max-attempts:5}") int maxAttempts) {
        return new OutboxRelayPublisher(outboxEventStore, rabbitTemplate, exchange, batchSize, maxAttempts);
    }
}
