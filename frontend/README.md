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

Proven end-to-end against the Gateway's own live `/v3/api-docs` (generated
`GatewayApiClient` covers the Phase 6 canary endpoints: `canary-admin`,
`customer-lookup-canary`). The 9 real business services are only reachable through the
Gateway's `/api-docs/<service>/v3/api-docs` proxy routes, which requires the full
docker-compose stack (Postgres, RabbitMQ, Keycloak, and every service) running — not
available in this sandbox. Generating those clients happens either via local Docker
(`docker compose up` per `deployment/docker/README.md`, then run the script per-service)
or as a future CI step once a portal feature actually needs one.

## Linting/formatting

```bash
npm run lint
npx prettier --check .
```
