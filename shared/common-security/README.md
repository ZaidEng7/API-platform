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

## Known limitations

- No object-level authorization framework — intentionally left to each service, since "does this Investor own this Portfolio" is domain logic, not shared plumbing.
- No token exchange / on-behalf-of support yet.
