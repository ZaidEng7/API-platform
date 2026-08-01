# Portfolio Service Runbook

System of Record for Portfolio positions (guide §8.3). First real enforcement of guide §12.2's object-level authorization (BOLA/IDOR) — an Investor caller only ever sees their own portfolios. Second hop of the Portfolio → Fund Service → fund-mgmt-adapter → legacy chain. Port 8088.

**Owning team:** Platform Engineering (placeholder — see `docs/phase-5-exit-criteria.md`).

## Health & metrics

- `GET /actuator/health`.
- Grafana → "Phase 5 Services" folder → **Phase 5 Service SLOs**, `$service = portfolio-service`.
- Prometheus job name: `portfolio-service`.

## Common scenarios

- **`403 PORTFOLIO-4030` on a read.** Working as intended, not a bug — an authenticated Investor tried to read a portfolio that isn't theirs. Staff roles (`operations`/`portfolio-manager`/`auditor`/`compliance`) aren't subject to this check.
- **`GET /{id}/valuation` fails or times out.** Valuation calls Fund Service's NAV endpoint per distinct fund held, and is all-or-nothing (no partial results) — one fund's NAV being unavailable fails the whole valuation. Check Fund Service's own health/logs first (`PORTFOLIO-5031` = fund-service unreachable, `PORTFOLIO-4043` = a held fund has no NAV yet).
- **Investment Service's `PortfolioPositionClient` can't record a position.** This is the saga's "confirm" step (`INV-5034` in Investment Service's logs = portfolio-service unreachable). Investment Service is designed to leave the subscription `AWAITING_PAYMENT` and safe to retry on this failure — it does not silently drop the confirmation.
- **Service won't start.** Check Postgres/RabbitMQ connectivity first.

## Deploy / rollback

No persistent dev/staging/prod environment exists in this project yet (`docs/ci-cd.md`). Once one does: `helm rollback portfolio-service <previous-revision>`, or redeploy the previous image tag from `ghcr.io/<owner>/api-platform-portfolio-service`.
