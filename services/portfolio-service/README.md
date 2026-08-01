# Portfolio Service

Owns Portfolio positions for a Party (guide Phase 5 item 6; target System-of-Record for "Portfolio positions", §8.3).

**First real enforcement of guide §12.2's object-level authorization rule**: "an Investor sees *their* portfolio only... every `GET /portfolios/{id}` must verify ownership — this is the #1 API vulnerability class (BOLA/IDOR)." `common-security`'s `CurrentUser` class was written with this exact rule in its own Javadoc back when it was built (Phase 3), but nothing used it for authorization until now — KYC/AML only ever used it to attribute a decision (`decidedBy`), not to gate access. `Portfolio.ownerId` is treated as directly comparable to the JWT `sub` claim (`CurrentUser.subject()`) — there's no Identity-to-Party linkage table anywhere in this platform yet to build a richer mapping from.

Also the **second real inter-service consumer chain** in this codebase: `GET /{id}/valuation` calls Fund Service's own NAV endpoint, extending the chain Fund Service started (Portfolio → Fund Service → `fund-mgmt-adapter` → legacy Fund Management product). No FX conversion — all positions are assumed to already be in the portfolio's own currency (no FX-rate source exists in this platform yet).

## Run locally

```bash
mvn -pl services/portfolio-service -am spring-boot:run
```

Requires PostgreSQL (`DB_HOST`/`DB_PORT`/`DB_NAME`/`DB_USER`/`DB_PASSWORD`, defaults target `localhost:5432/portfolio_service`), RabbitMQ (`RABBITMQ_HOST`/`RABBITMQ_PORT`/`RABBITMQ_USERNAME`/`RABBITMQ_PASSWORD`, defaults target `localhost:5672`, guest/guest), and Fund Service reachable via `FUND_SERVICE_URI` (default `http://localhost:8087`) for the valuation endpoint.

## Endpoints

All require a role from `platform/identity/realm-export.json` once `PORTFOLIO_SERVICE_JWT_ISSUER_URI` is set. Reads (`GET`) are open to staff (`operations`/`portfolio-manager`/`auditor`/`compliance`) **or** `investor` — staff see any portfolio, an authenticated investor sees only portfolios where they're the `ownerId`. Writes (`POST`) are staff-only — no investor self-service flow yet.

- `POST /api/v1/portfolios` `{customerId, ownerId, name, currency}` — opens a portfolio. Publishes `portfolio.account.opened`.
- `GET /api/v1/portfolios/{id}` — `403 PORTFOLIO-4030` if an investor requests a portfolio they don't own.
- `GET /api/v1/portfolios?ownerId=&page=&size=` — an investor querying another owner's `ownerId` gets `403 PORTFOLIO-4030` too.
- `POST /api/v1/portfolios/{id}/positions` `{fundCode, quantity}` — records a holding. Publishes `portfolio.position.recorded`.
- `GET /api/v1/portfolios/{id}/positions?page=&size=` — same ownership rule as the parent portfolio.
- `GET /api/v1/portfolios/{id}/valuation` — calls Fund Service's `GET /api/v1/funds/{fundCode}/nav` per distinct fund held, computes market value per position and a total. If any fund's NAV can't be obtained, the whole valuation fails (`404 PORTFOLIO-4043` if a specific fund has no NAV data, `503 PORTFOLIO-5031` if Fund Service is unreachable) rather than silently omitting a position — a partial valuation would be misleading.
- `GET /actuator/health`, `GET /swagger-ui.html`

## Domain events (guide §8.4, §22)

Published via the outbox pattern (`common-messaging`) on the `domain-events` topic exchange, full `EventEnvelope` as payload: `portfolio.account.opened`, `portfolio.position.recorded`. See `PortfolioEventPublishingIntegrationTest` for a real end-to-end proof (Testcontainers Postgres + RabbitMQ).

## Testing note

`PortfolioControllerIntegrationTest`'s ownership tests use Spring Security Test's `jwt()` request post-processor, not `@WithMockUser` — `@WithMockUser` produces a `UsernamePasswordAuthenticationToken` whose username `CurrentUser.subject()` can't read (it only reads a real `JwtAuthenticationToken`'s `sub` claim), so it can't exercise the ownership-matching logic at all. Only `jwt()` lets a test assert a specific caller identity precisely enough to prove both the "matches" and "doesn't match" branches.

## Resilience

`FundNavClient`'s call to Fund Service uses connect/read timeouts only, same rationale as Fund Service's own call to `fund-mgmt-adapter` — the callee is one of our own services, not a legacy system, so the full §9.4 resilience table doesn't apply directly.

## Service-to-service authentication

`FundNavClient`'s calls to Fund Service now attach an OAuth2 Client Credentials Bearer token via `common-security`'s `ServiceAuthRequestInterceptor` — see ADR 0001 (`docs/adr/0001-service-to-service-authentication.md`). A no-op until `platform.security.service-auth.client-secret` is configured.

## Known limitations

- No FX conversion in valuation (see above).
- No investor self-service — an investor can view their own portfolios but can't open one or record a position themselves.
- No Client Portal (§8.3: "Read copies allowed in" also names Client Portal alongside Reporting Service, which does now consume `portfolio.*` events — Client Portal itself isn't built anywhere in this platform).

## Operations

- **Runbook:** `docs/runbooks/portfolio-service.md`.
- **Owning team:** Platform Engineering (placeholder — see `docs/phase-5-exit-criteria.md`).
