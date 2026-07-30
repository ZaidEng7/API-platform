# Audit Service

Immutable, append-only audit trail (guide §13) — consumes every domain event published to the `domain-events` exchange and records it; exposes a read-only API restricted to Auditor/Compliance roles.

## Run locally

```bash
mvn -pl platform/audit-service -am spring-boot:run
```

Requires PostgreSQL (`DB_HOST`/`DB_PORT`/`DB_NAME`/`DB_USER`/`DB_PASSWORD`, defaults target `localhost:5432/audit_service`) and RabbitMQ (`RABBITMQ_HOST`/`RABBITMQ_PORT`, defaults `localhost:5672`).

## Endpoints

- `GET /api/v1/audit-events?eventType=&page=&size=` — restricted to `AUDITOR`/`COMPLIANCE` roles via `@PreAuthorize`. Enforced independent of whether JWT validation is active at the filter-chain level yet (see `shared/common-security/README.md`).
- `GET /actuator/health`, `GET /swagger-ui.html`

## How it works

- Declares a durable **quorum queue** bound to the `domain-events` topic exchange (declared by `common-messaging`) with routing key `#` — every event published anywhere on the platform lands here.
- Deduplicates on the source event's id via `common-messaging`'s `IdempotencyGuard` before writing (guide §22).
- Writes are append-only: no update/delete path exists anywhere in the application layer.

## Known limitations

- **No real producer wired up yet.** No service in this codebase currently publishes domain events (see `shared/common-messaging`'s own README), so this consumer is built and tested but not yet exercised by real traffic — verified instead via a direct-publish integration test (see below).
- **`occurredAt` falls back to consumption time.** `OutboxRelayPublisher` only puts the raw payload on the wire, not the full `EventEnvelope` (which carries the real `occurredAt`) — see `DomainEventAuditListener`'s Javadoc.
- **`who`/`what`/`before-after` aren't structured fields** — the guide's §13 fields (who, what, when, where) require a consistent event schema across producers that doesn't exist yet with only one walking-skeleton service built. For now the full raw payload is stored and `actor` is left null; revisit once KYC/AML/Payment services define real audited actions.
- **`outbox_events` table exists but is unused** — `common-messaging`'s outbox and idempotent-consumer support are entity-scanned together; this service only needs the idempotency side. See the Flyway migration comment.
