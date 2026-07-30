# Common Messaging

Outbox pattern, idempotent-consumer support, and the standard event envelope (guide §8.4, §22). Add the dependency and it self-registers (active once RabbitMQ is on the classpath):

```xml
<dependency>
  <groupId>com.company.platform</groupId>
  <artifactId>common-messaging</artifactId>
</dependency>
```

## Required DDL (add to your own service's Flyway migrations)

Database-per-service (guide §8.2) means these tables live in the *owning* service's schema, not a shared one — this module ships the JPA mapping, not the migration:

```sql
CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(255) NOT NULL,
    aggregate_id VARCHAR(255) NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    payload TEXT NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    correlation_id VARCHAR(255),
    producer VARCHAR(255) NOT NULL,
    schema_version INT NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ
);
CREATE INDEX idx_outbox_events_status_created_at ON outbox_events (status, created_at);

CREATE TABLE processed_events (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL,
    consumer_name VARCHAR(255) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_processed_events_event_consumer UNIQUE (event_id, consumer_name)
);
```

## What it provides

- **`OutboxEventStore.write(...)`** — call inside your existing `@Transactional` domain-change method (enforced via `Propagation.MANDATORY`, so misuse fails fast rather than silently dual-writing).
- **`OutboxRelayPublisher`** — polls PENDING rows on a schedule and publishes to the `domain-events` topic exchange using `eventType` as the routing key (matches the `<domain>.<entity>.<event-past-tense>` naming convention directly). Retries on failure up to `platform.messaging.outbox-relay.max-attempts` (default 5), then marks FAILED.
- **`IdempotencyGuard.tryMarkProcessed(eventId, consumerName)`** — call inside your consumer's transaction; returns `false` if already processed.
- **`EventEnvelope<T>`** — the mandatory envelope shape.

## Configuration

```yaml
platform:
  messaging:
    exchange: domain-events          # topic exchange name
    outbox-relay:
      enabled: true
      interval-ms: 2000
      batch-size: 50
      max-attempts: 5
spring:
  rabbitmq:
    publisher-confirm-type: correlated  # guide §22: publisher confirms on
    publisher-returns: true
```

Each consuming service declares its own **quorum queues** bound to `domain-events` with its own DLQ (guide §22) — that's queue topology, not something this library can own generically.

## Known limitations

- No Testcontainers-backed integration test yet for the outbox/idempotency persistence paths — lands with `shared/common-test`. Only the envelope's JSON contract is unit-tested here.
- `OutboxRelayPublisher` publishes the raw JSON payload (not the full envelope) as the message body, with `eventId`/`correlationId` carried in AMQP message properties — consumers that want the full envelope shape on the wire should call `write()` with the envelope itself as the payload.
