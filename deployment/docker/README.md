# Observability Stack

Local-dev metrics/tracing/logging infrastructure (guide Phase 3 exit criteria — building incrementally: metrics first, then tracing, then logging).

## Metrics (Prometheus + Grafana)

```bash
docker compose -f deployment/docker/observability.yml up -d
```

- **Prometheus** — `http://localhost:9090`. Scrapes `/actuator/prometheus` on the Gateway (`:8080`), Customer Service (`:8081`), and Audit Service (`:8083`) every 5s. Uses `host.docker.internal` since these services run as plain JVM processes on the host, not (yet) in the same Docker network — that changes once Kubernetes/container deployment scaffolding lands.
- **Grafana** — `http://localhost:3000` (`admin`/`admin`, dev-only; anonymous viewer access also enabled for convenience). Prometheus datasource is pre-provisioned — no manual setup needed.

Requires each service to actually be running locally first (`mvn -pl <service> -am spring-boot:run`) for there to be anything to scrape.
