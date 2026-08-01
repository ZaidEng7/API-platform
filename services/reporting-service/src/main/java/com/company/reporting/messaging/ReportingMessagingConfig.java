package com.company.reporting.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Binds a quorum queue to the domain-events exchange with three routing
 * patterns — this is the first Phase 5 business consumer of anyone else's
 * events (Audit Service's own {@code "#"} binding is a platform-wide,
 * non-business audit trail, not a business read-model). Only the three
 * domains guide §8.3's SoR matrix lists "Reporting" as a read-copy
 * destination for: Fund/NAV, Portfolio positions, Payment status.
 */
@Configuration
public class ReportingMessagingConfig {

    public static final String QUEUE_NAME = "reporting-service.domain-events";

    @Bean
    public Queue reportingDomainEventsQueue() {
        return QueueBuilder.durable(QUEUE_NAME)
                .quorum()
                .build();
    }

    @Bean
    public Binding reportingFundEventsBinding(Queue reportingDomainEventsQueue, TopicExchange domainEventsExchange) {
        return BindingBuilder.bind(reportingDomainEventsQueue).to(domainEventsExchange).with("fund.#");
    }

    @Bean
    public Binding reportingPortfolioEventsBinding(Queue reportingDomainEventsQueue, TopicExchange domainEventsExchange) {
        return BindingBuilder.bind(reportingDomainEventsQueue).to(domainEventsExchange).with("portfolio.#");
    }

    @Bean
    public Binding reportingPaymentEventsBinding(Queue reportingDomainEventsQueue, TopicExchange domainEventsExchange) {
        return BindingBuilder.bind(reportingDomainEventsQueue).to(domainEventsExchange).with("payment.#");
    }
}
