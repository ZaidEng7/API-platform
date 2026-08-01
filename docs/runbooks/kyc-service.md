# KYC Service Runbook

System of Record for KYC status (guide §8.3). Deliberately no decisioning logic — a human Compliance reviewer supplies the actual outcome via `POST /{id}/decision`. Port 8092.

**Owning team:** Platform Engineering (placeholder — see `docs/phase-5-exit-criteria.md`).

## Health & metrics

- `GET /actuator/health`.
- Grafana → "Phase 5 Services" folder → **Phase 5 Service SLOs**, `$service = kyc-service`.
- Prometheus job name: `kyc-service`.

## Common scenarios

- **`PENDING` checks piling up.** This is expected to require human attention — there's no automatic decisioning, so a growing `PENDING` count is a Compliance review queue backing up, not a system fault. Query `GET /api/v1/kyc-checks?customerId=` to see backlog per customer; a platform-wide backlog view isn't built (no cross-customer "all pending" endpoint) — that'd be a natural follow-up if this queue becomes an operational concern.
- **A check gets `409 KYC-4090`.** Someone tried to decide an already-decided check. KYC decisions are one-shot by design (guide: "a KYC decision is made once per check; a changed mind means requesting a new check") — the fix is a new check via `POST /api/v1/kyc-checks`, not retrying the same one.
- **Investment Service's `KycCheckClient` reports every customer as not-approved.** Check whether KYC Service itself is reachable/healthy first (`INV-5032` in Investment Service's logs means "kyc-service is unavailable") before assuming a real compliance problem — the client treats a missing/PENDING/REJECTED check and an unreachable service differently only in the error it throws, not in the boolean it eventually returns for a genuinely absent-or-not-approved check.
- **Service won't start.** Same as every Phase 5 service: check Postgres/RabbitMQ connectivity first.

## Deploy / rollback

No persistent dev/staging/prod environment exists in this project yet (`docs/ci-cd.md`). Once one does: `helm rollback kyc-service <previous-revision>`, or redeploy the previous image tag from `ghcr.io/<owner>/api-platform-kyc-service`.
