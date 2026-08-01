# AML Service Runbook

System of Record for AML screening status (guide §8.3), "compliance gate for money movement" alongside KYC. Async by design (§10.3): `POST /api/v1/aml/screenings` → `202 Accepted`, poll `GET .../{id}`. Port 8093.

**Owning team:** Platform Engineering (placeholder — see `docs/phase-5-exit-criteria.md`).

## Health & metrics

- `GET /actuator/health`.
- Grafana → "Phase 5 Services" folder → **Phase 5 Service SLOs**, `$service = aml-service`.
- Prometheus job name: `aml-service`.

## Common scenarios

- **`IN_PROGRESS` screenings piling up.** Same story as KYC's `PENDING` backlog — no real watchlist/sanctions vendor is wired in, so a human Compliance reviewer supplies `CLEAR`/`HIT` via `POST .../result`, and Operations marks a technical failure via `POST .../fail`. A growing `IN_PROGRESS` count means that human step hasn't happened yet, not that the service is broken.
- **`409 AML-4090`.** A screening was already completed or failed, and someone tried to record a result/failure again — screenings are decided once. Request a new screening instead.
- **Investment Service's `AmlScreeningClient` reports every customer as not-clear.** Check `INV-5033` in Investment Service's logs first (means aml-service unreachable) before assuming a real HIT/backlog problem.
- **Service won't start.** Check Postgres/RabbitMQ connectivity first.

## Deploy / rollback

No persistent dev/staging/prod environment exists in this project yet (`docs/ci-cd.md`). Once one does: `helm rollback aml-service <previous-revision>`, or redeploy the previous image tag from `ghcr.io/<owner>/api-platform-aml-service`.
