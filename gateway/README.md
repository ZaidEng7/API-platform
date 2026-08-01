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

## Known limitations

- No rate limiting / API key validation for partners yet.
- The canary demo above is a mechanism demonstration, not real Phase 6 execution — there are no real consumers in this repo to migrate.
