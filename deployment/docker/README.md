# Observability Stack

Local-dev metrics/tracing/logging infrastructure (guide Phase 3 exit criteria — building incrementally: metrics first, then tracing, then logging).

## Metrics (Prometheus + Grafana)

```bash
docker compose -f deployment/docker/observability.yml up -d
```

- **Prometheus** — `http://localhost:9090`. Scrapes `/actuator/prometheus` on the Gateway (`:8080`), Customer Service (`:8081`), and Audit Service (`:8083`) every 5s. Uses `host.docker.internal` since these services run as plain JVM processes on the host, not (yet) in the same Docker network — that changes once Kubernetes/container deployment scaffolding lands.
- **Grafana** — `http://localhost:3000` (`admin`/`admin`, dev-only; anonymous viewer access also enabled for convenience). Prometheus datasource is pre-provisioned — no manual setup needed.

Requires each service to actually be running locally first (`mvn -pl <service> -am spring-boot:run`) for there to be anything to scrape.

## Tracing (Jaeger + OpenTelemetry)

Same `docker compose -f deployment/docker/observability.yml up -d` brings up Jaeger too.

- **Jaeger UI** — `http://localhost:16686`. OTLP ingestion enabled (`COLLECTOR_OTLP_ENABLED=true`), listening on `4317` (gRPC) and `4318` (HTTP).
- Services export via `micrometer-tracing-bridge-otel` + `opentelemetry-exporter-otlp`, `management.otlp.tracing.endpoint` defaulting to `http://localhost:4318/v1/traces` (override with `OTEL_EXPORTER_OTLP_ENDPOINT`). Sampling is 100% — fine for dev/demo, not for production volume.
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
