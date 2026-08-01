# Reporting Service Runbook

Holds no System of Record of its own — read-only materialized views over Fund/Portfolio/Payment domain events (guide §8.3: "Reporting" is a read-copy destination, never an owner). The first real business-service consumer of anyone else's domain events. No write endpoints exist. Port 8091.

**Owning team:** Platform Engineering (placeholder — see `docs/phase-5-exit-criteria.md`).

## Health & metrics

- `GET /actuator/health`.
- Grafana → "Phase 5 Services" folder → **Phase 5 Service SLOs**, `$service = reporting-service`.
- Prometheus job name: `reporting-service`.

## Common scenarios

- **A read-model looks stale, wrong, or missing.** Never fix this by writing directly to `fund_nav_view`/`portfolio_view`/`position_view`/`payment_transfer_view` — every row here exists only because `DomainEventReportingListener` consumed a real event, and hand-editing it just makes the read-model diverge further from the actual source of truth. Instead: check the `reporting-service.domain-events` RabbitMQ queue for unconsumed backlog or dead-lettered messages, and check whether the *producing* service (Fund/Portfolio/Payment) actually published the event you'd expect — this service can only be as current as what it's been sent.
- **A brand-new fund/portfolio/transfer never appears here at all.** Check the producing service published the event at all (its own `outbox_events` table, `PENDING` vs `PUBLISHED`) before assuming this service's consumer is broken — the outbox relay is a separate failure point one hop earlier.
- **`404 RPT-4041` / `404 RPT-4042`.** `RPT-4041` = no NAV data exists yet for that fund code; `RPT-4042` = no portfolio data exists yet for that portfolio id. Both usually mean the relevant "created"/"registered" event hasn't been consumed yet, not that the id is wrong.
- **Service won't start.** Check Postgres/RabbitMQ connectivity first — this service still needs both even though it makes no outbound REST calls.

## Deploy / rollback

No persistent dev/staging/prod environment exists in this project yet (`docs/ci-cd.md`). Once one does: `helm rollback reporting-service <previous-revision>`, or redeploy the previous image tag from `ghcr.io/<owner>/api-platform-reporting-service`.
