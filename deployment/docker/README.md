# Observability Stack

Local-dev metrics/tracing/logging infrastructure (guide Phase 3 exit criteria — building incrementally: metrics first, then tracing, then logging).

## Metrics (Prometheus + Grafana)

```bash
docker compose -f deployment/docker/observability.yml up -d
```

- **Prometheus** — `http://localhost:9090`. Scrapes `/actuator/prometheus` every 5s on the Gateway plus every Phase 5 service (Customer, KYC, AML, Document, Fund, Portfolio, Investment, Payment, Reporting) and Audit Service — see `prometheus.yml`'s full job list. Uses `host.docker.internal` since these services run as plain JVM processes on the host, not (yet) in the same Docker network — that changes once Kubernetes/container deployment scaffolding lands.
- **Grafana** — `http://localhost:3000` (`admin`/`admin`, dev-only; anonymous viewer access also enabled for convenience). Prometheus datasource is pre-provisioned (fixed `uid: prometheus`, so dashboard JSON can reference it deterministically) — no manual setup needed. A **Phase 5 Service SLOs** dashboard is pre-provisioned too (`grafana/provisioning/dashboards/json/phase-5-service-slo.json`), under the "Phase 5 Services" folder — a `$service` dropdown switches between the nine services, with panels for availability, error rate, and p95/p99 latency against guide §27's own NFR targets (≥ 99.9% monthly, p95 < 500ms, p99 < 800ms). Requires `management.metrics.distribution.percentiles-histogram.http.server.requests=true` on each service (already set) so Prometheus actually exports the histogram buckets `histogram_quantile()` needs — without it, only average latency would be available, not real percentiles.

Requires each service to actually be running locally first (`mvn -pl <service> -am spring-boot:run`) for there to be anything to scrape.

## Tracing (Jaeger + OpenTelemetry)

Same `docker compose -f deployment/docker/observability.yml up -d` brings up Jaeger too.

- **Jaeger UI** — `http://localhost:16686`. OTLP ingestion enabled (`COLLECTOR_OTLP_ENABLED=true`), listening on `4317` (gRPC) and `4318` (HTTP).
- Services export via `spring-boot-starter-opentelemetry`, `management.opentelemetry.tracing.export.otlp.endpoint` defaulting to `http://localhost:4318/v1/traces` (override with `OTEL_EXPORTER_OTLP_ENDPOINT`). Sampling is 100% — fine for dev/demo, not for production volume.
- W3C Trace Context (`traceparent` header) is Spring Boot's default propagation format, matching guide §15 directly — no extra config needed.
- Export fails silently (logged, non-fatal) when Jaeger isn't running — services stay fully functional either way.

## Logging (OpenSearch)

Same compose file brings up OpenSearch and OpenSearch Dashboards too.

- **OpenSearch** — `http://localhost:9200` (single-node, security plugin disabled — dev/local only, never in a real deployment).
- **OpenSearch Dashboards** — `http://localhost:5601`.
- Services write masked JSON logs to a local rolling file (`shared/common-logging`'s README) in addition to console. **No shipper is wired up yet** — see that README's "Known limitations" for why Filebeat was deliberately not used and what the real options are. This increment stands up and verifies the backend (cluster health, index/search round-trip, Dashboards reachable); getting logs from disk into it is follow-up work.

## Contract testing (Pact Broker)

```bash
docker compose -f deployment/docker/pact-broker.yml up -d
```

- **Pact Broker** — `http://localhost:9292`, backed by its own Postgres (separate from any service's own DB). Public read enabled, no auth — dev/local only.
- Scaffolding only: see `contracts/README.md` for what's actually wired up and what a real consumer needs to do to replace the template pair.
