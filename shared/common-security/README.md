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

- **JWT resource server**, active only when `issuer-uri` is set. Default policy: actuator health/info and OpenAPI docs are public, everything else requires a valid JWT.
- **`KeycloakRealmRoleConverter`** — maps Keycloak's `realm_access.roles` claim to `ROLE_*` Spring Security authorities (Spring's default converter only reads `scope`).
- **`CurrentUser`** — `subject()` / `hasRole(role)` helpers for object-level authorization checks in application code (guide §12.2 — BOLA/IDOR is the #1 API vulnerability class; the actual ownership check stays domain-specific per service).
- `@EnableMethodSecurity` so `@PreAuthorize` works out of the box.

## Known limitations

- No object-level authorization framework — intentionally left to each service, since "does this Investor own this Portfolio" is domain logic, not shared plumbing.
- No token exchange / on-behalf-of support yet.
