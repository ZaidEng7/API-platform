package com.company.platform.test;

import com.company.platform.test.containers.RabbitMqTestContainer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.RabbitMQContainer;

/** Adds RabbitMQ on top of {@link AbstractPostgresIntegrationTest} for services using common-messaging. */
public abstract class AbstractMessagingIntegrationTest extends AbstractPostgresIntegrationTest {

    protected static final RabbitMQContainer RABBITMQ = RabbitMqTestContainer.instance();

    @DynamicPropertySource
    static void rabbitProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", RABBITMQ::getHost);
        registry.add("spring.rabbitmq.port", RABBITMQ::getAmqpPort);
    }
}
