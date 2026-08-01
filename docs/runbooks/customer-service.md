# Customer Service Runbook

System of Record for Customer/Party (guide §8.3; interim SoR conflict with Onboarding/CRM still pending Phase 1 sign-off — see `docs/roadmap.md`). Port 8081.

**Owning team:** Platform Engineering (placeholder — see `docs/phase-5-exit-criteria.md`).

## Health & metrics

- `GET /actuator/health` — liveness/readiness.
- Grafana → "Phase 5 Services" folder → **Phase 5 Service SLOs**, `$service = customer-service` — availability, error rate, p95/p99 latency against guide §27 targets (99.9% / 500ms / 800ms).
- Prometheus job name: `customer-service`.

## Common scenarios

- **Service won't start.** Flyway/JPA fail fast if Postgres (`DB_HOST`/`DB_PORT`/`DB_NAME`) isn't reachable — check the DB first, not the app. Same for RabbitMQ (`RABBITMQ_HOST`/`RABBITMQ_PORT`) — `common-messaging`'s outbox relay needs a broker connection at startup.
- **`customer.party.created`/`customer.party.updated` not reaching consumers.** Check `outbox_events` table for rows stuck `PENDING` past several relay intervals (`platform.messaging.outbox-relay.interval-ms`, default 2000ms) — that means the relay itself is failing, not that nobody's publishing. Rows that flip to `FAILED` after `max-attempts` need manual investigation/republish; the relay doesn't retry those automatically.
- **KYC/AML/Investment Service calling this service get unexpected 403s.** Known platform-wide gap, not specific to this service: no service-to-service authentication exists anywhere yet. If `CUSTOMER_SERVICE_JWT_ISSUER_URI` gets set for real, every unauthenticated caller — including other internal services — starts failing. See the cross-cutting ADR (`docs/adr/`) once written.

## Deploy / rollback

No persistent dev/staging/prod environment exists in this project yet (`docs/ci-cd.md`) — CI validates the Docker image and Helm chart against an ephemeral `kind` cluster (`k8s-smoke-test`), not a real target. Once a real environment exists: rollback is `helm rollback customer-service <previous-revision>` (`deployment/helm/service-chart` + `deployment/helm/values/customer-service.yaml`), or redeploying the previous image tag from GHCR (`ghcr.io/<owner>/api-platform-customer-service`).
