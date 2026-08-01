# Payment Service Runbook

Interim System of Record for Payment/transfer status (guide §8.3 — the real target is a PSP + back-office ledger, neither of which exists in this repo). No real PSP integration — settlement is a human/finance-ops confirmation. Port 8090.

**Owning team:** Platform Engineering (placeholder — see `docs/phase-5-exit-criteria.md`).

## Health & metrics

- `GET /actuator/health`.
- Grafana → "Phase 5 Services" folder → **Phase 5 Service SLOs**, `$service = payment-service`.
- Prometheus job name: `payment-service`.

## Common scenarios

- **`PENDING` transfers piling up.** Expected, not a bug — every transfer needs an explicit `POST .../settle` or `POST .../fail` from Operations, since no real PSP callback exists to do this automatically. A growing `PENDING` count is a finance-ops queue backing up, not a system fault.
- **`409 PAY-4090`.** A transfer was already settled or failed, and someone tried to change its state again — settlement/failure is one-shot.
- **A request is rejected as `400 VALIDATION_FAILED` on `paymentMethodToken`.** Working as intended — the token must match `tok_.+` (guide §3.1/§12.5 PCI-DSS scope isolation, enforced structurally). A raw card/account number was submitted where a PSP token was expected; that's a caller-side integration bug, not something to relax here.
- **Missing `Idempotency-Key` header.** Also intentional (`400 VALIDATION_FAILED`) — every financial-effect POST requires a client-generated key (guide §12.3).
- **Service won't start.** Check Postgres/RabbitMQ connectivity first.

## Deploy / rollback

No persistent dev/staging/prod environment exists in this project yet (`docs/ci-cd.md`). Once one does: `helm rollback payment-service <previous-revision>`, or redeploy the previous image tag from `ghcr.io/<owner>/api-platform-payment-service`.
