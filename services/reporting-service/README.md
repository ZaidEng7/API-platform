# Reporting Service

Phase 5 item 9 — the last service in Phase 5's build order. Guide §8.3's System-of-Record matrix explicitly names "Reporting" as a **read-copy destination**, never a System of Record itself, for three entities:

| Entity | SoR (owner) | Read copies allowed in |
|---|---|---|
| Fund / NAV | Fund Service | Portfolio, **Reporting** |
| Portfolio positions | Portfolio Service | **Reporting**, Client Portal |
| Payment status | Payment Service | Investment, **Reporting** |

So this service holds no data of its own — every row in its three tables is a materialized, read-only copy built by consuming the owning service's domain events. There are **no write endpoints** anywhere in this service's REST API; the only writer is `DomainEventReportingListener`.

## First real business-service event consumer

Every Phase 5 service before this one has *published* domain events with zero real consumers (Audit Service's own `"#"` binding is a platform-wide audit trail, not a business read-model). This is the first service that actually builds something out of them: it deserializes the full `EventEnvelope` each producer's outbox relay puts on the wire (see any other Phase 5 service's `publish()` method) and upserts a local read-model row.

Consumed events (bound via `fund.#`, `portfolio.#`, `payment.#` — deliberately narrower than Audit's `"#"`, since this service only tracks the three domains the SoR matrix grants it):

- `fund.definition.registered`, `fund.nav.updated` → `fund_nav_view` (keyed by `fundCode`)
- `portfolio.account.opened` → `portfolio_view`; `portfolio.position.recorded` → `position_view` (append-only)
- `payment.transfer.requested`/`settled`/`failed` → `payment_transfer_view`

Every consumer method is idempotent on `eventId` via `common-messaging`'s `IdempotencyGuard` — the same dedup mechanism Audit Service's own listener already established. Upsert methods tolerate a later-lifecycle event (e.g. `fund.nav.updated`) arriving before its "creation" event, since RabbitMQ doesn't guarantee cross-routing-key delivery order for a single queue with multiple bindings.

## No investor self-service, deliberately

Every other Phase 5 service with reads (Portfolio, Investment, Payment) enforces the guide §12.2 BOLA/IDOR ownership rule for an `investor` caller. This service doesn't: the guide's own SoR matrix lists "Reporting" and "Client Portal" as two *separate* read-copy destinations for Portfolio positions — Reporting is the internal/back-office reporting surface, Client Portal (not built anywhere in this platform) would be the investor-facing one. All endpoints here are staff-only (`operations`/`portfolio-manager`/`auditor`/`compliance`).

## Run locally

```bash
mvn -pl services/reporting-service -am spring-boot:run
```

Requires PostgreSQL (`DB_HOST`/`DB_PORT`/`DB_NAME`/`DB_USER`/`DB_PASSWORD`, defaults target `localhost:5432/reporting_service`) and RabbitMQ (`RABBITMQ_HOST`/`RABBITMQ_PORT`/`RABBITMQ_USERNAME`/`RABBITMQ_PASSWORD`, defaults target `localhost:5672`, guest/guest). No outbound service calls exist, so nothing else needs to be reachable — but the read-models stay empty until Fund/Portfolio/Payment Service are also running and publishing.

## Endpoints

All require a role from `platform/identity/realm-export.json` once `REPORTING_SERVICE_JWT_ISSUER_URI` is set. All staff-only (see above) — no investor access.

- `GET /api/v1/reports/funds?page=&size=` / `GET /api/v1/reports/funds/{fundCode}` — `404 RPT-4041` if no NAV data exists for that fund yet.
- `GET /api/v1/reports/portfolios?ownerId=&page=&size=` / `GET /api/v1/reports/portfolios/{portfolioId}` — includes the portfolio's positions; `404 RPT-4042` if unknown.
- `GET /api/v1/reports/payments?customerId=&page=&size=` — `customerId` optional (omit for all).
- `GET /actuator/health`, `GET /swagger-ui.html`

## Known limitations

- Position data is append-only (`position_view`) — this service doesn't attempt to net multiple position-recorded events into a single running quantity per fund. That aggregation is Portfolio Service's job as the actual System of Record; re-deriving it here would risk drifting from the source of truth.
- No FX conversion, no time-series/historical NAV reporting, no PDF/CSV export, no scheduled report generation — the guide's own roadmap entry for this item ("9. Reporting Service") doesn't specify any of these, and none were invented speculatively.
- Publishes no events of its own — terminal in the event flow, same as Audit Service.

## Operations

- **Runbook:** `docs/runbooks/reporting-service.md`.
- **Owning team:** Platform Engineering (placeholder — see `docs/phase-5-exit-criteria.md`).
