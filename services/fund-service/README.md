# Fund Service

Owns the Fund catalog and NAV history (guide Phase 5 item 5; target System-of-Record for "Fund / NAV", §8.3).

The legacy Fund Management product is the *interim* SoR, reachable only through `integration/fund-mgmt-adapter` (guide §8.1: services never access another service's, or another product's, database directly). This is the **first real consumer** of a Phase 4 legacy-integration adapter in this codebase — `fund-mgmt-adapter` was built as a template with no real consumer yet (see its README and `docs/roadmap.md` Phase 4), and `FundNavClient` is what it was built for. Fund existence/definition itself isn't sourced from that adapter — it only exposes NAV lookup by an already-known fund code — so registering a fund here is staff-driven, same as Customer/KYC/AML/Document.

## Run locally

```bash
mvn -pl services/fund-service -am spring-boot:run
```

Requires PostgreSQL (`DB_HOST`/`DB_PORT`/`DB_NAME`/`DB_USER`/`DB_PASSWORD`, defaults target `localhost:5432/fund_service`), RabbitMQ (`RABBITMQ_HOST`/`RABBITMQ_PORT`/`RABBITMQ_USERNAME`/`RABBITMQ_PASSWORD`, defaults target `localhost:5672`, guest/guest), and `integration/fund-mgmt-adapter` reachable via `FUND_MGMT_ADAPTER_URI` (default `http://localhost:8086`) for the NAV-refresh endpoint.

## Endpoints

All require a role from `platform/identity/realm-export.json` once `FUND_SERVICE_JWT_ISSUER_URI` is set (open by default otherwise — see `common-security`'s README/Javadoc):

- `POST /api/v1/funds` `{fundCode, name, currency}` — registers a fund; `operations`/`portfolio-manager` roles. `currency` must be a 3-letter ISO 4217 code. Publishes `fund.definition.registered`. A duplicate `fundCode` returns `409 FUND-4091`.
- `GET /api/v1/funds/{fundCode}` / `GET /api/v1/funds?page=&size=` — `operations`/`portfolio-manager`/`auditor` roles.
- `POST /api/v1/funds/{fundCode}/nav/refresh` — calls `fund-mgmt-adapter`'s `GET /api/v1/funds/{fundCode}/nav`, stores the result as a new NAV snapshot, and publishes `fund.nav.updated`; `operations`/`portfolio-manager` roles. `404 FUND-4041` if the fund itself isn't registered (checked before ever calling the adapter); `503 FUND-5031` if the adapter is unreachable.
- `GET /api/v1/funds/{fundCode}/nav` — latest known NAV; same read roles. `404 FUND-4042` if no refresh has happened yet.
- `GET /api/v1/funds/{fundCode}/nav-history?page=&size=` — full NAV snapshot history; same read roles.
- `GET /actuator/health`, `GET /swagger-ui.html`

## Domain events (guide §8.4, §22)

Published via the outbox pattern (`common-messaging`) on the `domain-events` topic exchange, full `EventEnvelope` as payload: `fund.definition.registered`, `fund.nav.updated`. See `FundEventPublishingIntegrationTest` for a real end-to-end proof (Testcontainers Postgres + RabbitMQ, WireMock standing in for `fund-mgmt-adapter`).

## Resilience

`FundNavClient`'s call to `fund-mgmt-adapter` uses connect/read timeouts (2s/5s) but deliberately skips the full §9.4 resilience table (retry/circuit-breaker/bulkhead) that the adapters themselves apply to their *legacy* calls — the callee here is one of our own well-behaved Spring Boot services, not a legacy system, so that table's specific rationale doesn't transfer directly. See `FundNavClientConfig`'s Javadoc.

## Known limitations

- No real consumer of either `fund.*` event yet — Portfolio Service and Reporting Service (§8.3: "Read copies allowed in") are the natural first ones.
- `refreshNav` is manual/on-demand only — no scheduled poller. A real deployment would likely schedule this per the adapter's refresh SLA (its own fictional legacy system only republishes NAV once daily); adding that wasn't done here to avoid a timing-flaky test for a mechanism nothing yet depends on.
- `FundNavClient`'s call to `fund-mgmt-adapter` does **not** carry a service-to-service credential — deliberately, per ADR 0001 (`docs/adr/0001-service-to-service-authentication.md`): that's a legacy-adapter boundary, not a peer-service call, and out of scope for that decision. Portfolio/Investment Service's own calls to *other business services* do carry one.

## Operations

- **Runbook:** `docs/runbooks/fund-service.md`.
- **Owning team:** Platform Engineering (placeholder — see `docs/phase-5-exit-criteria.md`).
