# Frontend

Angular 22 multi-project workspace for the Enterprise API Platform, per
`enterprise-api-platform-guide-v2.md` §5.2. Three projects:

- `projects/shared` — publishable Angular library (ng-packagr): layout shell, OIDC auth
  config, HTTP interceptors, notifications, error handling, and generated API clients.
  Consumed by both portals as `shared`.
- `projects/client-portal` — customer-facing portal (port 4200).
- `projects/admin-portal` — internal ops/review portal (port 4201, placeholder only —
  auth wiring deferred to Phase C, see below).

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
`shared`. Client Portal is wired against the `gateway-portal` Keycloak client
(registered for `http://localhost:4200/*`). **Admin Portal's own OIDC wiring is blocked**
on registering its redirect URI (port 4201) in
`platform/identity/realm-export.json` — either by extending `gateway-portal` or adding a
dedicated client — this is a backend/config change, not something to work around from
the frontend.

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
Phase A) and, as of Phase B, against four real business services through the Gateway's
`/api-docs/<service>/v3/api-docs` proxy routes: `PortfolioApiClient`,
`InvestmentApiClient`, `KycApiClient`, `AmlApiClient` — used by Client Portal's My
Portfolio, My Subscriptions, and Compliance Status features. Generating a client for any
of the remaining business services needs the full docker-compose stack (Postgres,
RabbitMQ, Keycloak, and the target service) running locally first.

Each backend `@RequestMapping` must declare `produces = MediaType.APPLICATION_JSON_VALUE`
for its generated client to work at all — without it, springdoc documents the response
as the default wildcard media type, and openapi-generator's TypeScript template sets
Angular's `HttpClient` `responseType: 'blob'` instead of `'json'`, silently breaking
response parsing even though the real runtime response is genuinely JSON. Portfolio,
Investment, KYC, and AML Service controllers already declare this; the remaining
business services will need the same one-line fix before their specs can be used here.

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
