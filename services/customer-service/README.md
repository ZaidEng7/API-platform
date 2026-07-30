# Customer Service

Walking-skeleton business service (guide Phase 3 exit criteria) — owns the Customer/Party entity per the System-of-Record matrix (§8.3, pending Phase 1 sign-off on the Onboarding/CRM conflict).

## Run locally

```bash
mvn -pl services/customer-service -am spring-boot:run
```

Requires a PostgreSQL instance reachable via `DB_HOST`/`DB_PORT`/`DB_NAME`/`DB_USER`/`DB_PASSWORD` env vars (defaults target `localhost:5432/customer_service`).

## Endpoints

- `GET /api/v1/customers/{id}`
- `POST /api/v1/customers`
- `GET /actuator/health`
- `GET /swagger-ui.html`

## Known limitations

- No auth yet (Identity/Keycloak lands later in Phase 3).
- No event publishing/outbox yet (lands with `shared/common-messaging`).
- No audit writes yet (lands with the Audit Service).
