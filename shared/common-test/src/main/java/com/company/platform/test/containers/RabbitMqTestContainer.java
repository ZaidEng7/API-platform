package com.company.platform.test.containers;

import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

/** Singleton container pattern — see {@link PostgresTestContainer}. */
public final class RabbitMqTestContainer {

    private static final RabbitMQContainer INSTANCE =
            new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.13-alpine"))
                    .withReuse(true);

    static {
        INSTANCE.start();
    }

    private RabbitMqTestContainer() {
    }

    public static RabbitMQContainer instance() {
        return INSTANCE;
    }
}
