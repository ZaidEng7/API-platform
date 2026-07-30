# Kubernetes Deployment

## Structure

- **`Dockerfile`** (repo root) — one generic multi-stage build reused for every service via build args, rather than a near-duplicate Dockerfile per service:
  ```bash
  docker build --build-arg MODULE=gateway --build-arg JAR_NAME=gateway-1.0.0-SNAPSHOT.jar -t gateway .
  docker build --build-arg MODULE=services/customer-service --build-arg JAR_NAME=customer-service-1.0.0-SNAPSHOT.jar -t customer-service .
  docker build --build-arg MODULE=platform/audit-service --build-arg JAR_NAME=audit-service-1.0.0-SNAPSHOT.jar -t audit-service .
  ```
  Non-root user, pinned base images (Maven+Temurin 21 for build, Temurin 21 JRE for runtime) — guide §20.

- **`deployment/helm/service-chart/`** — one generic Helm chart for every service (guide §17: "one Helm chart pattern for all"), parameterized via values rather than duplicated per service.
- **`deployment/helm/values/<service>.yaml`** — per-service values (image repo, port, env vars).
- **`deployment/environments/<env>/values.yaml`** — per-environment values (replica count, resource tier, autoscaling). `prod/values.yaml` deliberately omits `image.tag` — prod deploys must pass an explicit immutable tag via `--set`, never a floating one committed to a file.
- **`deployment/environments/<env>/namespace.yaml`** + **`network-policy-default-deny.yaml`** — one namespace per environment (`dev`/`test`/`staging`/`prod`), default-deny-all NetworkPolicy plus the one universal exception every pod needs (DNS egress) — guide §5.3, §23.

## Deploy

```bash
kubectl apply -f deployment/environments/dev/namespace.yaml
kubectl apply -f deployment/environments/dev/network-policy-default-deny.yaml -n api-platform-dev

helm install gateway deployment/helm/service-chart \
  -f deployment/helm/values/gateway.yaml \
  -f deployment/environments/dev/values.yaml \
  --set image.repository=<registry>/gateway \
  --set image.tag=<tag> \
  -n api-platform-dev
```

## Known gaps

- **No allow policies beyond DNS.** Default-deny-all blocks all pod-to-pod and pod-to-database traffic until an explicit allow policy is added — this scaffolding doesn't yet include service-specific ingress/egress rules (e.g., "gateway → customer-service", "customer-service → its Postgres"), since the real production topology (which DB/broker deployment story, ingress controller, etc.) isn't decided yet.
- **No Postgres/RabbitMQ deployment included.** `customer-service.yaml`/`audit-service.yaml` reference K8s Service DNS names (`customer-service-postgres`, `rabbitmq`) and `Secret` references (`optional: true` so the chart doesn't hard-fail without them) that don't exist yet — this chart deploys the *application*, not its data stores.
- **`kind`'s default CNI does not enforce NetworkPolicy.** The CI smoke test (`k8s-smoke-test`) proves the chart deploys correctly and the app becomes healthy on a real cluster — it does *not* prove default-deny is actually enforced, since that needs a NetworkPolicy-capable CNI (Calico/Cilium) that kind doesn't ship by default.
