# Common Security

Shared OAuth2/JWT resource-server wiring for business services (guide §12). Inactive until a service configures an issuer URI, so it's safe to add now even before Identity/Keycloak exists.

```xml
<dependency>
  <groupId>com.company.platform</groupId>
  <artifactId>common-security</artifactId>
</dependency>
```

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: https://keycloak.internal/realms/company
```

## What it provides

- **JWT resource server** — a `SecurityFilterChain` bean is always registered (deliberately: `spring-boot-starter-security` on the classpath alone makes Spring Boot auto-configure a default full-lockdown chain with a generated password unless something else defines one first). Fully open until `issuer-uri` is set; once set, actuator health/info and OpenAPI docs stay public and everything else requires a valid JWT. `@PreAuthorize` still enforces roles even while the chain is open, since it checks the current (anonymous, when unauthenticated) principal independently of the filter chain.
- **`KeycloakRealmRoleConverter`** — maps Keycloak's `realm_access.roles` claim to `ROLE_*` Spring Security authorities (Spring's default converter only reads `scope`).
- **`CurrentUser`** — `subject()` / `hasRole(role)` helpers for object-level authorization checks in application code (guide §12.2 — BOLA/IDOR is the #1 API vulnerability class; the actual ownership check stays domain-specific per service).
- `@EnableMethodSecurity` so `@PreAuthorize` works out of the box.
- **`ServiceAuthTokenProvider` / `ServiceAuthRequestInterceptor`** — service-to-service authentication (ADR 0001, `docs/adr/0001-service-to-service-authentication.md`). Fetches and caches an OAuth2 Client Credentials token against the `api-platform-services` confidential client `platform/identity/realm-export.json` already provisions, and attaches it as a Bearer header to outbound `RestClient` calls. Add the interceptor bean via `RestClient.Builder#requestInterceptor(...)` on any client calling another one of our own services (not a legacy-system adapter — see the ADR). A no-op — no token fetched, no header added — until `platform.security.service-auth.client-secret` is configured, same "stays open until configured" policy the resource-server wiring above already uses.

```yaml
platform:
  security:
    service-auth:
      client-id: api-platform-services   # default; matches the realm export
      client-secret: ${SERVICE_AUTH_CLIENT_SECRET:}   # blank = interceptor no-ops
```

## Known limitations

- No object-level authorization framework — intentionally left to each service, since "does this Investor own this Portfolio" is domain logic, not shared plumbing.
- No token exchange / on-behalf-of support yet.
- Service-to-service auth doesn't cover calls to Phase 4 legacy-integration adapters (e.g. Fund Service → `fund-mgmt-adapter`) — those are adapter/legacy-system boundaries, not peer-service calls, and out of scope for ADR 0001.
- Not yet exercised against a real running Keycloak instance in this repo's own tests — `ServiceAuthTokenProviderTest`/`ServiceAuthRequestInterceptorTest` prove the token-fetch/caching/header-attachment mechanics against a WireMock stub, not a real client-credentials grant against Keycloak itself.
