# Customer Service

Business service (guide Phase 3 walking-skeleton, extended in Phase 5) — owns the Customer/Party entity per the System-of-Record matrix (§8.3, pending Phase 1 sign-off on the Onboarding/CRM conflict).

## Run locally

```bash
mvn -pl services/customer-service -am spring-boot:run
```

Requires a PostgreSQL instance reachable via `DB_HOST`/`DB_PORT`/`DB_NAME`/`DB_USER`/`DB_PASSWORD` env vars (defaults target `localhost:5432/customer_service`), and a RabbitMQ instance via `RABBITMQ_HOST`/`RABBITMQ_PORT`/`RABBITMQ_USERNAME`/`RABBITMQ_PASSWORD` (defaults target `localhost:5672`, guest/guest).

## Endpoints

- `GET /api/v1/customers/{id}`
- `GET /api/v1/customers?query=&page=&size=` — search by fullName/email substring (case-insensitive), paginated
- `POST /api/v1/customers`
- `PUT /api/v1/customers/{id}` — updates `fullName`/`phone`/`dateOfBirth`; `email`/`partyType` are immutable via this endpoint (identity fields, not profile edits)
- `GET /actuator/health`
- `GET /swagger-ui.html`

## Domain events (guide §8.4, §22)

Publishes via the outbox pattern (`common-messaging`) on the `domain-events` topic exchange:

- `customer.party.created` — on `POST /api/v1/customers`
- `customer.party.updated` — on `PUT /api/v1/customers/{id}`

Both carry a full `EventEnvelope` as the message payload. See `shared/common-messaging/README.md` for the mechanism and `CustomerEventPublishingIntegrationTest` for a real end-to-end proof (Testcontainers Postgres + RabbitMQ).

## Known limitations

- No auth yet (Identity/Keycloak lands later in Phase 3; this service wasn't in scope for Phase 3's identity wiring the way Audit Service was — revisit before any real deployment).
- No audit writes yet from this service's own actions (Audit Service consumes domain events platform-wide, but doesn't distinguish "who changed this Customer" beyond what's in the event payload).
- `PartyType` is a two-value enum (`INDIVIDUAL`/`ORGANIZATION`) with no organization-specific fields yet (e.g. registration number, beneficial owners) — add when a real consumer needs them, not speculatively.
