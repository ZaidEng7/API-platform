# Document Service

Owns document metadata/references for a Party (guide Phase 5 item 4, "KYC needs it" — document review feeds KYC decisions).

Guide §8.3's System-of-Record matrix lists this service's read copies as "— (references only)": no other service keeps a copy, and this service itself never stores the actual file bytes. `storageReference` is an opaque pointer supplied by the caller — where it actually points (a future DMS-product adapter per guide §9, an object store) is deliberately out of scope here, since building real blob storage or a legacy DMS integration would be inventing infrastructure this platform doesn't have yet (Phase 1/4 legacy integration is still deferred). Never logging document *contents* (guide §14) falls out of this structurally: this entity never holds content in the first place, only a pointer to it.

## Run locally

```bash
mvn -pl services/document-service -am spring-boot:run
```

Requires PostgreSQL (`DB_HOST`/`DB_PORT`/`DB_NAME`/`DB_USER`/`DB_PASSWORD`, defaults target `localhost:5432/document_service`) and RabbitMQ (`RABBITMQ_HOST`/`RABBITMQ_PORT`/`RABBITMQ_USERNAME`/`RABBITMQ_PASSWORD`, defaults target `localhost:5672`, guest/guest).

## Endpoints

All require a role from `platform/identity/realm-export.json` once `DOCUMENT_SERVICE_JWT_ISSUER_URI` is set (open by default otherwise — see `common-security`'s README/Javadoc):

- `POST /api/v1/documents` `{customerId, documentType, storageReference}` — registers document metadata; `operations`/`compliance`/`customer-service` roles. Publishes `customer.document.uploaded`.
- `GET /api/v1/documents/{id}` — `operations`/`compliance`/`customer-service`/`auditor` roles.
- `GET /api/v1/documents?customerId=&page=&size=` — history for a Party, paginated; same roles as above.
- `POST /api/v1/documents/{id}/verify` `{notes}` — **`compliance` role only**. Publishes `customer.document.verified`.
- `POST /api/v1/documents/{id}/reject` `{notes}` — **`compliance` role only**. Publishes `customer.document.rejected`.
- A document can only be reviewed once (verify or reject) — a second attempt returns `409 DOC-4090`.
- `GET /actuator/health`, `GET /swagger-ui.html`

## Domain events (guide §8.4, §22)

Published via the outbox pattern (`common-messaging`) on the `domain-events` topic exchange, full `EventEnvelope` as payload. `storageReference` is deliberately **not** included in the `customer.document.uploaded` payload — no reason for every consumer of that event to receive the storage pointer just because it exists. See `DocumentEventPublishingIntegrationTest` for a real end-to-end proof (Testcontainers Postgres + RabbitMQ) covering all three event types.

## Known limitations

- No real consumer of any `customer.document.*` event yet — KYC Service is the natural first one (the guide's own rationale for this service existing).
- No actual file upload/storage — see the rationale above. A real DMS adapter or object-store integration would populate/validate `storageReference`, not change this service's public contract.
