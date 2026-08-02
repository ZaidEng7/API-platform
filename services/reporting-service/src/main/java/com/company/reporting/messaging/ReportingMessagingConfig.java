package com.company.reporting.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Binds a quorum queue to the domain-events exchange with several routing
 * patterns — this is the first Phase 5 business consumer of anyone else's
 * events (Audit Service's own {@code "#"} binding is a platform-wide,
 * non-business audit trail, not a business read-model). Guide §8.3's SoR
 * matrix lists "Reporting" as a read-copy destination for: Fund/NAV,
 * Portfolio positions, Payment status — and, as of the Admin Portal's
 * cross-customer review queues, KYC checks, AML screenings, Documents, and
 * Subscriptions are now tracked here too.
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

    @Bean
    public Binding reportingKycEventsBinding(Queue reportingDomainEventsQueue, TopicExchange domainEventsExchange) {
        return BindingBuilder.bind(reportingDomainEventsQueue).to(domainEventsExchange).with("customer.kyc.#");
    }

    @Bean
    public Binding reportingAmlEventsBinding(Queue reportingDomainEventsQueue, TopicExchange domainEventsExchange) {
        return BindingBuilder.bind(reportingDomainEventsQueue).to(domainEventsExchange).with("customer.aml.#");
    }

    @Bean
    public Binding reportingDocumentEventsBinding(Queue reportingDomainEventsQueue, TopicExchange domainEventsExchange) {
        return BindingBuilder.bind(reportingDomainEventsQueue).to(domainEventsExchange).with("customer.document.#");
    }

    @Bean
    public Binding reportingSubscriptionEventsBinding(Queue reportingDomainEventsQueue, TopicExchange domainEventsExchange) {
        return BindingBuilder.bind(reportingDomainEventsQueue).to(domainEventsExchange).with("investment.#");
    }
}
