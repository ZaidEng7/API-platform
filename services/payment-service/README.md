# Payment Service

Owns payment/transfer status — Phase 5 item 8. Guide §8.3 target SoR for "Payment status" is a real PSP + a back-office ledger; neither exists in this repo (no PSP adapter was built in Phase 4), so this service is the interim SoR, and settlement is a human/finance-ops confirmation rather than a real PSP callback — the same "human/future-service supplies the missing piece" pattern KYC/AML/Document/Investment Service already established.

## What's here, and what isn't

A `Transfer` starts `PENDING` on `POST /api/v1/payments` and ends `SETTLED` or `FAILED` via an explicit, staff-only confirmation (`POST /{id}/settle` / `POST /{id}/fail`) — there is no outbound call to a real payment service provider anywhere in this module's code. Because there's no outbound PSP call, guide §12.4's "never blind-retry a payment POST" doesn't have a call site to apply to *within this service*; it's captured here as guidance for whichever real PSP adapter eventually gets built on top of this.

`reference` is a caller-supplied, service-agnostic string (e.g. a subscription id) — this service doesn't hold a hard dependency on Investment Service or any other specific caller.

## PCI-DSS scope isolation (guide §3.1/§12.5)

"Card data never enters the platform; use tokenization via PSP." Enforced structurally, not just by convention: `paymentMethodToken` must match `tok_.+` (`RequestTransferRequest`) — a raw card/account number is rejected as a `400 VALIDATION_FAILED` before it's ever persisted. A real integration would validate against whichever PSP's actual token format is in use; this is a template pattern proving the shape is enforced at the API boundary.

## Idempotency (guide §12.3)

`POST /api/v1/payments` requires a client-generated `Idempotency-Key` header (missing → `400 VALIDATION_FAILED`). A replayed request with a previously-seen key returns the *original* transfer (still `202`, not a new row) rather than creating a duplicate — same pattern Investment Service established for `POST /api/v1/subscriptions`.

## Async pattern (guide §10.3)

`POST /api/v1/payments` returns `202 Accepted` + `Location`, never holding the HTTP connection open — same shape AML Service's `POST /api/v1/aml/screenings` established. Poll `GET /{id}` for the current status.

## Run locally

```bash
mvn -pl services/payment-service -am spring-boot:run
```

Requires PostgreSQL (`DB_HOST`/`DB_PORT`/`DB_NAME`/`DB_USER`/`DB_PASSWORD`, defaults target `localhost:5432/payment_service`) and RabbitMQ (`RABBITMQ_HOST`/`RABBITMQ_PORT`/`RABBITMQ_USERNAME`/`RABBITMQ_PASSWORD`, defaults target `localhost:5672`, guest/guest). No downstream service calls exist, so nothing else needs to be reachable.

## Endpoints

All require a role from `platform/identity/realm-export.json` once `PAYMENT_SERVICE_JWT_ISSUER_URI` is set. Reads are open to staff (`operations`/`portfolio-manager`/`auditor`/`compliance`) **or** `investor` (own transfers only, same ownership pattern Portfolio/Investment Service established — `403 PAY-4030` otherwise). Requesting a transfer is staff-only (`operations`/`portfolio-manager`); settling/failing a transfer is `operations`-only, narrower than the request role, since confirming money actually moved is a more sensitive action than initiating the request.

- `POST /api/v1/payments` `{customerId, ownerId, amount, currency, paymentMethodToken, reference}` + `Idempotency-Key` header — `202 Accepted` + `Location`.
- `GET /api/v1/payments/{id}` / `GET /api/v1/payments?ownerId=&page=&size=`
- `POST /api/v1/payments/{id}/settle` — `409 PAY-4090` if not `PENDING`.
- `POST /api/v1/payments/{id}/fail` `{reason}` — `409 PAY-4090` if not `PENDING`.
- `GET /actuator/health`, `GET /swagger-ui.html`

## Domain events (guide §8.4, §22)

Published via the outbox pattern (`common-messaging`) on the `domain-events` topic exchange, full `EventEnvelope` as payload: `payment.transfer.requested`, `payment.transfer.settled` (the exact event name the guide's own §22 naming example uses), `payment.transfer.failed`.

## Known limitations

- No real PSP integration — settlement is human-confirmed (see above).
- No outbound service-to-service authentication applies to this service today since it makes no outbound service calls (see ADR 0001, `docs/adr/0001-service-to-service-authentication.md`, and Investment Service's README for the two relationships it does cover) — relevant to whichever real PSP adapter is built on top of it later.
- No refund/reversal flow — only forward settlement/failure, since no PSP integration exists to reverse against.

## Operations

- **Runbook:** `docs/runbooks/payment-service.md`.
- **Owning team:** Platform Engineering (placeholder — see `docs/phase-5-exit-criteria.md`).
