# KYC Service

Owns KYC-status decisions for a Party (guide Phase 5 item 2; target System-of-Record for "KYC status" per §8.3).

`customerId` is a bare reference to Customer Service's Party id — no cross-service database access (§8.1), and no attempt to validate the id is real (that would mean calling Customer Service synchronously on the hot path for something this service doesn't otherwise need). Deliberately has **no decisioning logic** — no rules engine, no sanctions-list screening, no fraud scoring. Those are real compliance business rules that don't exist yet (regulatory jurisdiction sign-off, §3.1, is still pending — see project memory / `docs/roadmap.md` Phase 2). This service owns the review's lifecycle and status; a human Compliance reviewer supplies the actual decision via the API. AML Service (Phase 5 item 3) and a real rules engine are the natural place for automated decisioning later — not invented here.

## Run locally

```bash
mvn -pl services/kyc-service -am spring-boot:run
```

Requires PostgreSQL (`DB_HOST`/`DB_PORT`/`DB_NAME`/`DB_USER`/`DB_PASSWORD`, defaults target `localhost:5432/kyc_service`) and RabbitMQ (`RABBITMQ_HOST`/`RABBITMQ_PORT`/`RABBITMQ_USERNAME`/`RABBITMQ_PASSWORD`, defaults target `localhost:5672`, guest/guest).

## Endpoints

All require a role from `platform/identity/realm-export.json` once `KYC_SERVICE_JWT_ISSUER_URI` is set (open by default otherwise — see `common-security`'s README/Javadoc):

- `POST /api/v1/kyc-checks` `{customerId}` — starts a PENDING check; `operations`/`compliance`/`customer-service` roles. Publishes `customer.kyc.requested`.
- `GET /api/v1/kyc-checks/{id}` — `operations`/`compliance`/`customer-service`/`auditor` roles.
- `GET /api/v1/kyc-checks?customerId=&page=&size=` — history for a Party, paginated; same roles as above.
- `POST /api/v1/kyc-checks/{id}/decision` `{outcome: APPROVED|REJECTED, reason}` — **`compliance` role only** (the realm's own role description: "Compliance/AML/KYC review and sign-off"). Publishes `customer.kyc.approved` or `customer.kyc.rejected` — a check can only be decided once; deciding an already-decided check returns `409 KYC-4090`.
- `GET /actuator/health`, `GET /swagger-ui.html`

## Domain events (guide §8.4, §22)

`customer.kyc.approved`/`customer.kyc.rejected` are the guide's own §22 naming example verbatim. Published via the outbox pattern (`common-messaging`) on the `domain-events` topic exchange, full `EventEnvelope` as payload. See `KycEventPublishingIntegrationTest` for a real end-to-end proof (Testcontainers Postgres + RabbitMQ) covering all three event types.

## Known limitations

- No real consumer of any `customer.kyc.*` event yet (Customer Service's own read model, per §8.3's "Read copies allowed in" column, is the natural first one).
- `decidedBy` is only populated when the caller authenticates via a real JWT (`CurrentUser.subject()`) — stays `null` under the default open dev-mode chain or in tests using `@WithMockUser`, which doesn't produce a `JwtAuthenticationToken`.
- No linkage back to Customer Service's own KYC-status read model — that's Customer Service's side of §8.3's "Read copies allowed in" column, not built yet.

## Operations

- **Runbook:** `docs/runbooks/kyc-service.md`.
- **Owning team:** Platform Engineering (placeholder — see `docs/phase-5-exit-criteria.md`).
