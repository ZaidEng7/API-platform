# Fund Service Runbook

System of Record for Fund / NAV (guide §8.3). First real consumer of a Phase 4 legacy-integration adapter — `FundNavClient` calls `integration/fund-mgmt-adapter`'s NAV endpoint. Port 8087.

**Owning team:** Platform Engineering (placeholder — see `docs/phase-5-exit-criteria.md`).

## Health & metrics

- `GET /actuator/health`.
- Grafana → "Phase 5 Services" folder → **Phase 5 Service SLOs**, `$service = fund-service`.
- Prometheus job name: `fund-service`.

## Common scenarios

- **NAV looks stale.** NAV refresh is manual/on-demand only (`POST /api/v1/funds/{fundCode}/nav/refresh`) — there's deliberately no scheduler yet (avoids a timing-flaky test for a mechanism nothing currently depends on). If NAV hasn't updated, someone needs to call refresh; it isn't happening on its own.
- **`503 FUND-5031`.** `fund-mgmt-adapter` is unreachable — check that service's own health before assuming a Fund Service problem. Portfolio Service's valuation calls, and Portfolio Service's own `FundNavClient`, will surface the same failure one hop further downstream.
- **`404 FUND-4041` / `404 FUND-4042`.** `FUND-4041` = the fund code itself was never registered (`POST /api/v1/funds`); `FUND-4042` = the fund exists but no NAV snapshot has been fetched for it yet (see "NAV looks stale" above).
- **Service won't start.** Check Postgres/RabbitMQ connectivity first.

## Deploy / rollback

No persistent dev/staging/prod environment exists in this project yet (`docs/ci-cd.md`). Once one does: `helm rollback fund-service <previous-revision>`, or redeploy the previous image tag from `ghcr.io/<owner>/api-platform-fund-service`.
