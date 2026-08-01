# AML Service

Owns AML/watchlist screening for a Party (guide Phase 5 item 3, "compliance gates for all money movement" alongside KYC Service).

Async by design, per the guide's own §10.3 example (`POST /aml/screenings` → `202 Accepted`, poll `GET /aml/screenings/{id}` for status) — never hold the HTTP connection open waiting on screening completion. `customerId` is a bare reference to Customer Service's Party id — no cross-service database access (§8.1).

Deliberately has **no real watchlist/sanctions-matching logic**. That's an anti-corruption-layer adapter (guide §9) this platform doesn't have yet — Phase 1/4 legacy integration is still deferred, and inventing a fictional sanctions-matching algorithm would be worse than not having one. Instead: a human Compliance reviewer supplies the CLEAR/HIT determination via the API (same "human supplies the missing piece" pattern as KYC Service's decision endpoint), and Operations can mark a screening technically `FAILED` — distinct from a compliance `HIT`, representing e.g. a future watchlist vendor adapter being unavailable.

## Run locally

```bash
mvn -pl services/aml-service -am spring-boot:run
```

Requires PostgreSQL (`DB_HOST`/`DB_PORT`/`DB_NAME`/`DB_USER`/`DB_PASSWORD`, defaults target `localhost:5432/aml_service`) and RabbitMQ (`RABBITMQ_HOST`/`RABBITMQ_PORT`/`RABBITMQ_USERNAME`/`RABBITMQ_PASSWORD`, defaults target `localhost:5672`, guest/guest).

## Endpoints

All require a role from `platform/identity/realm-export.json` once `AML_SERVICE_JWT_ISSUER_URI` is set (open by default otherwise — see `common-security`'s README/Javadoc):

- `POST /api/v1/aml/screenings` `{customerId}` — starts an `IN_PROGRESS` screening, returns **`202 Accepted`** with a `Location` header (not `201` — this is the guide's §10.3 async pattern, not a synchronous create); `operations`/`compliance`/`customer-service` roles. Publishes `customer.aml.requested`.
- `GET /api/v1/aml/screenings/{id}` — poll for status; `operations`/`compliance`/`customer-service`/`auditor` roles.
- `GET /api/v1/aml/screenings?customerId=&page=&size=` — history for a Party, paginated; same roles as above.
- `POST /api/v1/aml/screenings/{id}/result` `{outcome: CLEAR|HIT, notes}` — **`compliance` role only**. Publishes `customer.aml.cleared` or `customer.aml.flagged`. A screening can only be completed once; a second attempt returns `409 AML-4090`.
- `POST /api/v1/aml/screenings/{id}/fail` `{reason}` — **`operations` role only** (a system/technical failure, not a compliance judgment). Publishes `customer.aml.failed`. Also once-only — `409 AML-4090` on a second attempt.
- `GET /actuator/health`, `GET /swagger-ui.html`

## Domain events (guide §8.4, §22)

Published via the outbox pattern (`common-messaging`) on the `domain-events` topic exchange, full `EventEnvelope` as payload. See `AmlEventPublishingIntegrationTest` for a real end-to-end proof (Testcontainers Postgres + RabbitMQ) covering all four event types.

## Known limitations

- No real consumer of any `customer.aml.*` event yet.
- No real watchlist/sanctions vendor integration — see the module-level rationale above. When one is added (a Phase 4-style adapter), it would be the thing that eventually calls `POST .../result` or `.../fail` instead of a human, without needing to change this service's public contract.

## Operations

- **Runbook:** `docs/runbooks/aml-service.md`.
- **Owning team:** Platform Engineering (placeholder — see `docs/phase-5-exit-criteria.md`).
