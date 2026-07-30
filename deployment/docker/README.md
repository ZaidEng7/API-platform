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
