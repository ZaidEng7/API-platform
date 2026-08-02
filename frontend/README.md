# Frontend

Angular 22 multi-project workspace for the Enterprise API Platform, per
`enterprise-api-platform-guide-v2.md` §5.2. Three projects:

- `projects/shared` — publishable Angular library (ng-packagr): layout shell, OIDC auth
  config, HTTP interceptors, notifications, error handling, and generated API clients.
  Consumed by both portals as `shared`.
- `projects/client-portal` — customer-facing portal (port 4200).
- `projects/admin-portal` — internal ops/review portal (port 4201): role-gated queues for
  KYC/AML/document review, subscriptions, and payments (Phase C).

## Development servers

```bash
npm run start:client-portal   # http://localhost:4200
npm run start:admin-portal    # http://localhost:4201
```

## Building

```bash
npm run build:shared          # must run before building either portal
npm run build:client-portal
npm run build:admin-portal
npm run build                 # all three, in dependency order
```

## Unit tests

Vitest, via Angular's native `@angular/build:unit-test` builder (the builder only
supports `karma`/`vitest` — not Jest).

```bash
npm test
```

## Auth

Both portals authenticate against Keycloak (`platform/identity`) using the Auth Code +
PKCE flow via `angular-auth-oidc-client`, configured through `provideKeycloakAuth()` in
`shared`. Client Portal is wired against the `gateway-portal` Keycloak client (registered
for `http://localhost:4200/*`); Admin Portal has its own dedicated client, registered for
`http://localhost:4201/*`.

Admin Portal's role-based nav and route guards (`core/roles.ts`, `role.guard.ts`,
`default-redirect.guard.ts`) read realm roles off `CurrentUserService.roles` (see
`shared`'s `CurrentUserService`), sourced from the access token's `realm_access.roles`
claim. Because that signal is built from a one-shot token read, Admin Portal's
`app.config.ts` uses `provideAppInitializer` to block bootstrap — and therefore the
Router's initial navigation and every guard — until `OidcSecurityService.checkAuth()`
has fully processed the redirect callback. Without this, a route guard or component can
read `CurrentUserService` before the callback resolves and freeze that session's
roles/subjectId at their empty initial value for good, since the underlying Observable
never re-emits. Client Portal doesn't need this: it has no route guards, and only reads
`CurrentUserService` from components that render after its own `isAuthenticated()` gate
is already true.

## Generated API clients

`scripts/generate-api-client.sh <service-name> <spec-url>` generates a typed Angular
client (via `openapi-generator`, `typescript-angular` generator) into
`projects/shared/src/lib/generated-api/<service-name>/`, exported from `shared`'s
`public-api.ts` under a per-service namespace (e.g. `GatewayApiClient`) — namespaced
because each generated client has its own `Configuration`/`BASE_PATH`/`provideApi`/`APIS`
symbols that would collide if flattened across services.

```bash
npm run codegen:gateway   # proof-of-concept: Gateway's own aggregated spec
npm run codegen -- <service-name> <spec-url>   # generic, for any other service
```

Proven end-to-end against the Gateway's own live `/v3/api-docs` (`GatewayApiClient`,
Phase A) and, as of Phase C, against seven real business services through the Gateway's
`/api-docs/<service>/v3/api-docs` proxy routes: `PortfolioApiClient`,
`InvestmentApiClient`, `KycApiClient`, `AmlApiClient`, `DocumentApiClient`,
`PaymentApiClient`, `ReportingApiClient` — used by Client Portal's My Portfolio, My
Subscriptions, and Compliance Status features, and by every Admin Portal review-queue
feature. Generating a client for any of the remaining business services (Fund, Customer,
Audit) needs Postgres + RabbitMQ + Keycloak + the target service reachable locally first
— there's no single docker-compose file for the whole stack yet, each service is started
individually (`DB_*`/`RABBITMQ_*`/`<SERVICE>_JWT_ISSUER_URI` env vars, see each service's
own `application.yml` for its defaults).

Each backend `@RequestMapping` must declare `produces = MediaType.APPLICATION_JSON_VALUE`
for its generated client to work at all — without it, springdoc documents the response
as the default wildcard media type, and openapi-generator's TypeScript template sets
Angular's `HttpClient` `responseType: 'blob'` instead of `'json'`, silently breaking
response parsing even though the real runtime response is genuinely JSON. This bites
_any_ controller the first time it gets a real generated-client consumer, not just
newly-written ones — `ReportingController` and Payment Service's `TransferController`
both still lacked it in Phase C despite predating that phase, since neither had a
frontend consumer before then. Portfolio, Investment, KYC, AML, Document, Payment,
Reporting, and (as of Phase D) the Gateway's own `CustomerLookupCanaryController` all
declare it now; Fund/Customer/Audit still don't (no frontend consumer yet — fix this the
moment one is added, before debugging anything else about an empty response). Note that
`CustomerLookupCanaryController` returns a raw `ResponseEntity<String>` (it proxies
whichever backend answered, and the two backends' response shapes genuinely differ) —
`produces` still fixes the generated client's `Accept`/`responseType` the same way, and
was confirmed live to pass the body through unmodified rather than double-encoding it.

A real cross-origin browser consumer of a Gateway endpoint (as opposed to a same-origin
static page) can also hit a CORS gap that a proxied business-service route never would:
`spring.cloud.gateway.globalcors` only covers requests Spring Cloud Gateway's own route
locator proxies, not plain `@RestController`s living in the Gateway app itself (like
`CustomerLookupCanaryController`). If a preflight `OPTIONS` request 401s, the Gateway's
`SecurityConfig` needs its own `.cors(...)` wiring, not just an origin allow-list in
YAML. Separately, any custom response header a component reads off `HttpResponse`
(`X-Canary-Target` here) needs `Access-Control-Expose-Headers` set explicitly — browsers
hide non-simple response headers from cross-origin JS by default even though the header
is genuinely present on the wire.

If a locally-running Angular dev server's build gets stuck failing with `NG2008: Could
not find stylesheet file` after creating a new component's `.ts`/`.scss` pair, it's a
file-write race (Vite's watcher reacting to the `.ts` file before the co-located `.scss`
file has actually landed on disk) — check the dev server's own log for the last
"Application bundle generation complete" vs. "failed", and `touch` any source file in the
same project to force a fresh rebuild attempt once both files genuinely exist.

## Resolving "my own id"

None of the backend services expose a `/me` endpoint — Portfolio/Investment Service
compare a caller-supplied `ownerId` query param to the JWT `sub` claim to enforce
ownership (guide §12.2), and KYC/AML Service do the same with `customerId`. `shared`'s
`CurrentUserService` decodes `sub` from the OIDC access token
(`OidcSecurityService.getPayloadFromAccessToken()`) and exposes it as a signal
(`subjectId`) — every investor-scoped feature reads this instead of calling a
non-existent "current user" endpoint.

## Docker

Each app is a separate multi-stage build (Angular CLI → static files → Nginx), using
`frontend/Dockerfile` as a build-arg-parameterized template (mirrors the root
`Dockerfile`'s pattern for the Java services) with `frontend/` itself as the build
context — the Angular workspace has no dependency on the Java modules.

```bash
docker build -f frontend/Dockerfile --build-arg PROJECT=client-portal -t client-portal frontend
docker build -f frontend/Dockerfile --build-arg PROJECT=admin-portal -t admin-portal frontend
```

`frontend/nginx.conf` serves `dist/<project>/browser` with a SPA fallback (`try_files
... /index.html`) for client-side routing, `Cache-Control: no-cache` on `index.html`, and
long-lived immutable caching on hashed build assets. Angular's `environment.ts` is baked
into the JS bundle at _build_ time, not read from the container's environment — there is
no runtime config.json/env-var mechanism yet, since every environment currently points at
the same localhost addresses (no real deployment domain exists yet).

Helm values for Client Portal (`deployment/helm/values/client-portal.yaml`) reuse the
same generic `service-chart` the Java services use, overriding `service.port`/
`probes.*Path` for a static Nginx container instead of a Spring Boot actuator.

## Linting/formatting

```bash
npm run lint
npx prettier --check .
```
