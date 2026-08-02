# API Gateway

Spring Cloud Gateway shell (guide §7). Routes each service's `/api/v1/**` prefix to its backend, generates/propagates `X-Correlation-Id`, enforces an explicit CORS allow-list (never `*`), and offloads coarse-grained JWT AuthN (see `SecurityConfig`) when Identity/Keycloak's `issuer-uri` is configured.

## Run locally

```bash
mvn -pl gateway -am spring-boot:run
```

Requires the routed services running (each `*_URI` property, defaulting to `http://localhost:<port>` — see `application.yml`).

## Phase 6 canary demo

`docs/roadmap.md` Phase 6 (strangler-fig consumer migration) has no real Web Portal/Mobile/partner consumer in this repo to migrate, so `/api/v1/customer-lookup/{id}` is a small standalone demonstration of the mechanism the guide describes, built on one real pair the guide's own §8.3 SoR matrix already flags as a Customer/Party ownership conflict:

- **Legacy path**: `integration/crm-adapter` (`GET /api/v1/crm-customers/{id}`)
- **Migrated target**: Customer Service (`GET /api/v1/customers/{id}`)

`CustomerLookupCanaryController` proxies each request to one or the other based on a runtime weight held in `CanaryWeightRegistry`, and stamps the response with `X-Canary-Target` so callers (and the demo page) can see which backend answered. The weight is changed live via `POST /admin/canary/customer-lookup?legacyWeightPercent=<0-100>` (gated to `ROLE_ADMINISTRATOR` once Identity is configured) — no restart, so rollback is an instant weight flip back to 0, matching the guide's "instant rollback = flag off."

This is deliberately a plain proxying `@RestController`, not a declarative Spring Cloud Gateway route: the stock `Weight` route filter reads its split from static YAML at startup, which can't deliver that instant-rollback property without a restart.

Open `/canary-demo.html` (served as a static resource by this module) for an interactive view — call the endpoint repeatedly and watch the target tally, or change the weight and see the split shift on the next call with no restart.

**Real cross-origin consumer (Frontend Phase D)**: Client Portal's Party Lookup feature calls this endpoint over a real cross-origin browser request (`http://localhost:4200` → `http://localhost:8080`), unlike `canary-demo.html` which is same-origin. This surfaced two gaps `canary-demo.html` never could: (1) `CustomerLookupCanaryController` had no `produces` declared, so its generated TypeScript client defaulted to blob-parsing the response (the same recurring gotcha other services have hit — see `docs/roadmap.md` Phase B/C/D notes); (2) `spring.cloud.gateway.globalcors` only covers requests proxied through Spring Cloud Gateway's own route locator, never plain `@RestController`s like this one — `SecurityConfig.java` now wires its own `CorsConfigurationSource` (same allowed-origins property) via `.cors(...)`, with `X-Canary-Target` explicitly listed in `Access-Control-Expose-Headers` (browsers hide custom response headers from cross-origin JS callers by default, even though the header is present on the wire either way).

## API catalog

Guide §7.28/Phase 7's "start with the OpenAPI specs + Swagger UI aggregation" step: every business service already exposes its own `/v3/api-docs` and `swagger-ui.html` (springdoc, annotated per-controller since the Phase 5 exit criteria work), but each was only reachable standalone, service by service. This module now aggregates all nine into one page:

- `/api-docs/<service>/**` routes (e.g. `/api-docs/customer-service/v3/api-docs`) proxy each service's own `/v3/api-docs`, rewriting the path so the service itself needs no changes.
- `springdoc.swagger-ui.urls` (see `application.yml`) lists all nine plus this Gateway's own spec (which now includes the Phase 6 canary endpoints, annotated the same way) in one dropdown at `/swagger-ui.html`.
- This is genuinely just aggregation, not a full developer portal (Backstage or a vendor tool) — that's explicitly a *later*, separate Phase 7 step per the guide, not done here.
- **Known limitation**: "Try it out" in the aggregated view calls whatever server URL each service's own OpenAPI spec advertises (its own direct address, e.g. `http://localhost:8081`) — not back through this Gateway's proxy path. Browsing/reading specs works correctly either way; only live in-browser test calls are affected. Not fixed here — would need each service's springdoc to pick up `X-Forwarded-*` headers (Spring Cloud Gateway sends some by default, but not a forwarded path prefix for this proxy shape) to advertise the Gateway-reachable URL instead.
- Once Identity's `issuer-uri` is configured, all of these paths fall under `SecurityConfig`'s default `anyExchange().authenticated()` — no explicit `permitAll` was added for docs, since this is an internal catalog, not a public one.

## Known limitations

- No rate limiting / API key validation for partners yet.
- The canary demo above is a mechanism demonstration, not real Phase 6 execution — there are no real consumers in this repo to migrate.
