# Investment Service

Drives the fund subscription saga guide §8.4 names explicitly: "validate customer → KYC/AML check → reserve units → collect payment → confirm → notify" (Phase 5 item 7 — "subscription/redemption sagas live here"). No distributed 2PC transaction; this service owns and persists the saga state, executes a compensating action on failure, and every write is idempotent.

Only **subscription** is built, not redemption — subscription alone already exercises the saga/compensation/idempotency/timeout machinery the guide cares about; redemption is a near-mirror-image flow that's a natural, low-risk follow-up once needed, not built speculatively here.

## The saga, as actually implemented

1. **Validate customer** — synchronous call to Customer Service. A missing customer is a plain request-validation failure (`404`), not a saga outcome — no `Subscription` row is created.
2. **KYC/AML check** — synchronous calls to KYC Service and AML Service, checking each customer's *most recent* check/screening. Both must be approved/clear.
3. **Reserve units** — if either compliance check fails, a `Subscription` is still created, directly in `FAILED` status with a `failureReason`, and `investment.subscription.failed` is published — the saga ran and failed, which is itself an auditable outcome, not silently swallowed. If both pass, the `Subscription` is created in `AWAITING_PAYMENT` and `investment.subscription.reserved` is published. "Reserve units" here just means this row existing — there's no separate fund-inventory concept to hold against (Fund Service prices NAV, it doesn't track a finite unit supply; mutual funds create units on subscription rather than drawing from a fixed pool).
4. **Collect payment** — has no real callee. Payment Service is Phase 5 item 8, built *after* this one in the guide's own dependency order, so there's nothing to call yet. Modeled as a human/finance-ops confirmation step instead of a fake PSP integration — the same "human/future-service supplies the missing piece" pattern KYC/AML/Document Service already established.
5. **Confirm** — `POST /{id}/confirm-payment` calls Portfolio Service to materialize the actual position, then transitions to `CONFIRMED` and publishes `investment.subscription.confirmed` — the *exact* event name the guide's own §22 naming example uses.
6. **Notify** — is the event publication itself; no separate notification channel is built (matches how Audit Service already consumes every domain event platform-wide).

Steps 1–3 run **synchronously** inside the initiating `POST` — all three downstream services are our own fast REST APIs, not a legacy batch process, so there's no need to persist intermediate state between them. Only step 4 (a genuinely long wait) needs durable state, a timeout, and a dead-letter path.

**Compensation**: `POST /{id}/cancel` (the guide's own exact example endpoint, `POST /subscriptions/{id}/cancel`) is the compensating action for a subscription that never gets paid. There's no separate inventory hold to release — cancelling *is* the full compensation, since "reserved" never meant more than this row's own state.

## Idempotency (guide §12.3)

`POST /api/v1/subscriptions` requires a client-generated `Idempotency-Key` header (missing → `400 VALIDATION_FAILED`). A replayed request with a previously-seen key returns the *original* subscription (still `201`, not a new row) rather than re-running the saga — proven in `SubscriptionControllerIntegrationTest` by asserting Customer Service is only called once across two identical requests.

## Timeout / dead-letter path (guide §8.4)

`SubscriptionTimeoutJob` is a `@Scheduled` bean (reusing the same mechanism `common-messaging`'s own `OutboxRelayPublisher` already established in this codebase) that finds `AWAITING_PAYMENT` subscriptions past their `timeoutAt` and transitions them to `TIMED_OUT`, publishing `investment.subscription.timed-out` — the guide's "a stuck subscription must page someone, not silently rot" signal a real alerting pipeline would page on. `investment.subscription.timeout` (default `PT15M`) and `investment.subscription.timeout-check-interval-ms` (default 60000) are both configurable; `SubscriptionTimeoutJobIntegrationTest` shrinks both to prove the job really runs, not just that the code compiles.

## Service-to-service authentication

Each of this service's four downstream `RestClient`s (Customer/KYC/AML/Portfolio Service) now attaches an OAuth2 Client Credentials Bearer token via `common-security`'s `ServiceAuthRequestInterceptor` — see ADR 0001 (`docs/adr/0001-service-to-service-authentication.md`) for why and `ExternalServiceClientConfig`'s own Javadoc for the wiring. It's a no-op (no header attached, same as before) until `platform.security.service-auth.client-secret` is configured, so nothing changes in dev/test until then. Not yet exercised against a real running Keycloak instance — only against a WireMock stub standing in for the token endpoint (`common-security`'s own tests) and against downstream services with `issuer-uri` unset (this service's own WireMock-based tests, which don't enforce security at all either way).

## Run locally

```bash
mvn -pl services/investment-service -am spring-boot:run
```

Requires PostgreSQL (`DB_HOST`/`DB_PORT`/`DB_NAME`/`DB_USER`/`DB_PASSWORD`, defaults target `localhost:5432/investment_service`), RabbitMQ (`RABBITMQ_HOST`/`RABBITMQ_PORT`/`RABBITMQ_USERNAME`/`RABBITMQ_PASSWORD`, defaults target `localhost:5672`, guest/guest), and Customer/KYC/AML/Portfolio Service all reachable (`CUSTOMER_SERVICE_URI`, `KYC_SERVICE_URI`, `AML_SERVICE_URI`, `PORTFOLIO_SERVICE_URI` — see the known gap above for why calling them for real won't currently work once they're secured).

## Endpoints

All require a role from `platform/identity/realm-export.json` once `INVESTMENT_SERVICE_JWT_ISSUER_URI` is set. Reads are open to staff (`operations`/`portfolio-manager`/`auditor`/`compliance`) **or** `investor` (own subscriptions only, same ownership pattern Portfolio Service established — `403 INV-4030` otherwise). Writes are staff-only — no investor self-service, same known limitation Portfolio Service documented.

- `POST /api/v1/subscriptions` `{customerId, ownerId, portfolioId, fundCode, quantity}` + `Idempotency-Key` header — runs the saga's synchronous steps.
- `GET /api/v1/subscriptions/{id}` / `GET /api/v1/subscriptions?ownerId=&page=&size=`
- `POST /api/v1/subscriptions/{id}/confirm-payment` — `409 INV-4091` if not `AWAITING_PAYMENT`.
- `POST /api/v1/subscriptions/{id}/cancel` — `409 INV-4091` if not `AWAITING_PAYMENT`.
- `GET /actuator/health`, `GET /swagger-ui.html`

## Domain events (guide §8.4, §22)

Published via the outbox pattern (`common-messaging`) on the `domain-events` topic exchange, full `EventEnvelope` as payload: `investment.subscription.reserved`, `investment.subscription.failed`, `investment.subscription.confirmed`, `investment.subscription.cancelled`, `investment.subscription.timed-out`.

## Known limitations

- Service-to-service auth is wired but unverified against a real Keycloak instance (see above).
- Redemption not built (see above).
- `confirmPayment`'s call to Portfolio Service is best-effort transactional: the local DB commit and the remote call aren't atomic. If the remote call fails, the local transaction rolls back and the subscription stays `AWAITING_PAYMENT` (safe to retry); the narrower risk is a partial-failure window where Portfolio Service's call *succeeds* but this service then fails to commit before returning. A production system would use an outbox-driven retry for this call too (the same pattern this service already uses for its own domain events), not built here to keep focus on the saga's own state machine.
- No real Payment Service integration (see the saga description above).
- No four-eyes/segregation-of-duties check for large redemptions (guide §12.2 mentions this) — not applicable yet since redemption itself isn't built.

## Operations

- **Runbook:** `docs/runbooks/investment-service.md`.
- **Owning team:** Platform Engineering (placeholder — see `docs/phase-5-exit-criteria.md`).
