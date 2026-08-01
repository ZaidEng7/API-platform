# Investment Service Runbook

Drives the fund subscription saga (guide §8.4). Calls Customer, KYC, AML, and Portfolio Service — the most connected service in the platform, and the one where the platform-wide "no service-to-service auth" gap has the most surface area. Port 8089.

**Owning team:** Platform Engineering (placeholder — see `docs/phase-5-exit-criteria.md`).

## Health & metrics

- `GET /actuator/health`.
- Grafana → "Phase 5 Services" folder → **Phase 5 Service SLOs**, `$service = investment-service`.
- Prometheus job name: `investment-service`.

## Common scenarios

- **⚠️ Any of Customer/KYC/AML/Portfolio Service's `issuer-uri` gets configured for real.** This is the single most important thing to know before an on-call engineer touches this service: none of the four outbound calls carry any service-to-service credential today. The moment any of those services actually enforces auth, this saga's calls to it start failing with 403 — see this service's own README "Known gap" section and the platform-wide ADR once written (`docs/adr/`). This is not a bug to "fix" quickly; it needs a real service-identity mechanism.
- **Subscriptions stuck `AWAITING_PAYMENT`.** `SubscriptionTimeoutJob` runs on a schedule (`investment.subscription.timeout-check-interval-ms`, default 60000ms) and times a subscription out after `investment.subscription.timeout` (default `PT15M`), publishing `investment.subscription.timed-out` — the guide's own "a stuck subscription must page someone" signal. If timed-out events aren't appearing at all, check whether the scheduled job itself is running (not just whether subscriptions are stuck).
- **`INV-4044`, `INV-5031`/`5032`/`5033`/`5034`.** `INV-4044` = the customer doesn't exist (checked before any saga step runs). The `INV-503x` codes each mean one specific downstream service is unreachable: `5031` Customer, `5032` KYC, `5033` AML, `5034` Portfolio — check that specific service's own health/runbook, not this one.
- **`409 INV-4091`.** Someone tried to confirm-payment or cancel a subscription that's no longer `AWAITING_PAYMENT` (already confirmed, cancelled, failed, or timed out).
- **Service won't start.** Check Postgres/RabbitMQ connectivity first.

## Deploy / rollback

No persistent dev/staging/prod environment exists in this project yet (`docs/ci-cd.md`). Once one does: `helm rollback investment-service <previous-revision>`, or redeploy the previous image tag from `ghcr.io/<owner>/api-platform-investment-service`.
