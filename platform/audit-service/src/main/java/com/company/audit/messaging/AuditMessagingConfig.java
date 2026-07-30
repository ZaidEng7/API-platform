package com.company.audit.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Binds a quorum queue to the domain-events exchange (declared by
 * common-messaging) with a wildcard routing key, so every event published
 * anywhere on the platform reaches the audit trail (guide §13, §22).
 */
@Configuration
public class AuditMessagingConfig {

    public static final String QUEUE_NAME = "audit-service.domain-events";

    @Bean
    public Queue auditDomainEventsQueue() {
        return QueueBuilder.durable(QUEUE_NAME)
                .quorum()
                .build();
    }

    @Bean
    public Binding auditDomainEventsBinding(Queue auditDomainEventsQueue, TopicExchange domainEventsExchange) {
        return BindingBuilder.bind(auditDomainEventsQueue).to(domainEventsExchange).with("#");
    }
}
