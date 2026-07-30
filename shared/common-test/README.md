# Common Test

Testcontainers base classes so integration tests hit real Postgres/RabbitMQ, not H2 or mocks (guide §20). Add as a **test-scoped** dependency:

```xml
<dependency>
  <groupId>com.company.platform</groupId>
  <artifactId>common-test</artifactId>
  <scope>test</scope>
</dependency>
```

## What it provides

- **`AbstractPostgresIntegrationTest`** — `@SpringBootTest` + `@AutoConfigureMockMvc`, wires `spring.datasource.*` to a shared singleton Postgres container.
- **`AbstractMessagingIntegrationTest`** — extends the above, adds a shared singleton RabbitMQ container.

Both use the Testcontainers "singleton container" pattern: one container per JVM/test run, started once and left for Ryuk to reap — not started/stopped per test class, which would be far slower across a growing service suite.

```java
class CustomerControllerIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void createsAndFetchesCustomer() throws Exception {
        // real Postgres, real Flyway migrations, real HTTP round-trip
    }
}
```

## Known limitations

- Requires a local Docker daemon to run these tests — same as any Testcontainers setup, nothing platform-specific.
- No Keycloak/Testcontainers module yet — once Identity lands, secured-endpoint integration tests will need one.
