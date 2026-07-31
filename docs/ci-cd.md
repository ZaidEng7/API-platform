# CI/CD Pipeline

Maps to the guide's §21 pipeline: `build → unit tests → Sonar gate → SAST/dependency scan → Docker build + Trivy scan → integration tests → Pact contract verification → publish → deploy dev/test/staging (auto) → prod (manual approval)`.

## What's running today

| Guide stage | Implementation | Where |
|---|---|---|
| Build → unit tests → integration tests | `mvn -B verify` (Testcontainers-backed integration tests run here too — real Postgres/RabbitMQ, not mocks) | `build-and-test` job, `.github/workflows/ci.yml` |
| SAST | CodeQL, weekly scheduled scan + every push/PR | `.github/workflows/codeql.yml` |
| Dependency scan | Dependabot (Maven, GitHub Actions, Docker ecosystems) | `.github/dependabot.yml` |
| Docker build + Trivy scan | Builds all 3 service images, fails on CRITICAL vulnerabilities with a known fix (`ignore-unfixed: true` — no point failing the pipeline over a CVE nobody can patch yet) | `container-scan-and-publish` job |
| Publish | Pushes to GHCR (`ghcr.io/<owner>/api-platform-<service>`, tagged `<git-sha>` and `latest`) using the built-in `GITHUB_TOKEN` — no extra secrets needed. **Only on push to `main`**, never from PRs/feature branches | `container-scan-and-publish` job |
| Sonar gate | Wired up, but **inactive until you configure it** — see below | `sonar-quality-gate` job |
| Pact contract verification | Tooling + one template consumer/provider pair (not real business behavior — see below) | `pact-contract-verification` job |

Plus the environment-specific smoke tests built alongside each Phase 3 increment (`identity-smoke-test`, `metrics-smoke-test`, `tracing-smoke-test`, `logging-smoke-test`, `k8s-smoke-test`) — these aren't in the guide's generic pipeline, they're this project's way of proving each piece of infrastructure actually works, since there's no Docker daemon available in the local dev sandbox this was built in.

## What needs your action

- **Sonar quality gate** — needs a SonarCloud account linked to this repo (SonarCloud is free for public repos). Once you have it:
  1. Add repo secret `SONAR_TOKEN`.
  2. Add repo variable `SONAR_ORGANIZATION` (your SonarCloud org key).
  3. Add repo variable `SONAR_ENABLED` = `true` — this is the actual on/off switch (GitHub Actions doesn't allow the `secrets` context in a job-level `if:` condition, so the gate has to be a plain variable rather than checking `SONAR_TOKEN` directly).
  4. Optionally add repo variable `SONAR_PROJECT_KEY` (defaults to `ZaidEng7_API-platform` if unset).

  The job is already gated on `SONAR_ENABLED == 'true'` — it'll start running with no other changes once you've done the above. I can't create the SonarCloud account or generate that token myself.

## Pact contract testing — tooling only, not real contracts yet

The Gateway is still a dumb path-based reverse proxy (`Path=/api/v1/customers/**` → forward), not a typed consumer of Customer Service's API — there's no *real* contract to verify yet. Rather than wait, what's built here is the toolchain plus one clearly-labeled template pair proving the whole pipeline works end to end:

- `deployment/docker/pact-broker.yml` — self-hosted Pact Broker (`pactfoundation/pact-broker:2.142.0-pactbroker2.120.0`) + its own Postgres.
- `contracts/customer-consumer-example/` — a template consumer: a minimal HTTP client + a Pact consumer test (`pact-jvm-consumer-junit5` 4.6.17) that defines one interaction against `GET /api/v1/customers/{id}` and publishes it to the broker via `mvn pact:publish`.
- `services/customer-service`'s `CustomerServicePactVerificationTest` — the provider side (`pact-jvm-provider-junit5spring`), verifying the real controller (with the application service stubbed, no live DB needed) against whatever's published to the broker. Tagged `@Tag("pact")` and excluded from the default `mvn verify`/`build-and-test` run — see `services/customer-service/pom.xml`'s `pact-verification` Maven profile — because it needs the broker up and the consumer's pact already published, which only the `pact-contract-verification` CI job guarantees.

Both interactions check response *shape* only, not real business rules — see `contracts/README.md`. Replace this pair with your own once a real consumer of another service's API exists (guide §20 calls Pact "the single most important addition for a 10+ service platform").

## What's not built yet, and why

- **Automated deploy to dev/test/staging, manual-approval prod** — no persistent environments exist to deploy to. The only Kubernetes cluster in this project is the ephemeral `kind` cluster `k8s-smoke-test` spins up and tears down per CI run — proving the Helm chart *works*, not a real deployment target. Standing up actual dev/test/staging/prod clusters (cloud provider, ingress, real Postgres/RabbitMQ, DNS, TLS) is real infrastructure work and its own decision, not something to default into.
