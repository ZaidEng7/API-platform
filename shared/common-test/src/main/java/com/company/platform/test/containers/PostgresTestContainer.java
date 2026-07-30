package com.company.platform.test.containers;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Testcontainers "singleton container" pattern: one container per JVM,
 * started once and left running (Ryuk cleans it up at JVM exit) rather than
 * started/stopped per test class — real Postgres, no H2-only suites
 * (guide §20).
 */
public final class PostgresTestContainer {

    private static final PostgreSQLContainer<?> INSTANCE =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("test")
                    .withUsername("test")
                    .withPassword("test")
                    .withReuse(true);

    static {
        INSTANCE.start();
    }

    private PostgresTestContainer() {
    }

    public static PostgreSQLContainer<?> instance() {
        return INSTANCE;
    }
}
