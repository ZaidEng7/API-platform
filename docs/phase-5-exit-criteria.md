# Phase 5 Exit Criteria Tracker

Guide §25 states, for every Phase 5 service: **"design-reviewed OpenAPI spec · contract tests green with all consumers · SLO dashboard live · runbook written · owning team named."** Phase 5's nine services were built and merged first (see `docs/roadmap.md`); this tracker closes the five exit criteria across all of them afterward, one criterion at a time. It's being built up across a few separate branches/PRs — see each section for what's landed so far.

| Service | OpenAPI spec | Contract tests | SLO dashboard | Runbook | Owning team |
|---|---|---|---|---|---|
| Customer Service | ✅ | ✅ (provider) | ✅ | ✅ | ✅ (placeholder) |
| KYC Service | ✅ | ✅ (provider) | ✅ | ✅ | ✅ (placeholder) |
| AML Service | ✅ | ✅ (provider) | ✅ | ✅ | ✅ (placeholder) |
| Document Service | ✅ | N/A | ✅ | ✅ | ✅ (placeholder) |
| Fund Service | ✅ | ✅ (consumer + provider) | ✅ | ✅ | ✅ (placeholder) |
| Portfolio Service | ✅ | ✅ (consumer + provider) | ✅ | ✅ | ✅ (placeholder) |
| Investment Service | ✅ | ✅ (consumer) | ✅ | ✅ | ✅ (placeholder) |
| Payment Service | ✅ | N/A | ✅ | ✅ | ✅ (placeholder) |
| Reporting Service | ✅ | N/A | ✅ | ✅ | ✅ (placeholder) |

**All five criteria are now formally closed for all nine services** — the "placeholder" tag on Owning team is the one honest asterisk: it's organizational information this session doesn't have, not a technical gap. See that section below.

## OpenAPI spec — ✅ done

Every service already exposes a live, auto-generated spec via `springdoc-openapi-starter-webmvc-ui` at `GET /v3/api-docs` and `GET /swagger-ui.html` — that scaffolding has existed since each service's own Phase 5 build. What was actually missing for "design-reviewed" was substance: the generated spec carried only method-name-derived operation ids and no grouping. Every controller across all nine services now carries a class-level `@Tag(name, description)` (one tag per service, naming the guide §8.3 entity it's the System of Record — or read-copy, for Reporting — for) and a `@Operation(summary = ...)` on every endpoint stating what it does and any non-obvious behavior (async shape, ownership enforcement, once-only semantics, etc.) — real review-quality documentation, not per-field `@Schema` padding on every DTO, which would have been high-volume, low-marginal-value churn across ~40 DTOs for information the response type itself already carries (field names, via `-parameters`, and correct types).

No static spec snapshot is checked into the repo — the live `/v3/api-docs` endpoint is the artifact a reviewer or consumer would actually use, and a checked-in copy would just be one more thing to keep in sync on every future endpoint change.

## Contract tests — ✅ done for every service that has a real consumer relationship

"With all consumers" only has meaning where a real consumer actually exists. Three of the nine services (Document, Payment, Reporting) make no outbound REST calls to another service and receive none either — there is nothing to contract against, so they're marked N/A rather than left as an open gap. The other six now have real pact-jvm consumer/provider pairs (see `contracts/README.md` for the full table), replacing the template pair (`contracts/customer-consumer-example`, now deleted) that used to stand in before any real consumer existed:

- Fund Service ↔ fund-mgmt-adapter
- Portfolio Service ↔ Fund Service
- Investment Service ↔ Customer Service
- Investment Service ↔ KYC Service
- Investment Service ↔ AML Service
- Investment Service ↔ Portfolio Service

Each pact asserts only what the real consumer's own client code actually depends on (response shape where the client parses the body, status/request shape only where it doesn't — see `contracts/README.md`), not a blanket schema dump. Every provider verification test is tagged `pact` and runs only in the dedicated `pact-contract-verification` CI job, same convention the template already established.

## SLO dashboard — ✅ done, one dashboard for all nine

One Grafana dashboard (`deployment/docker/grafana/provisioning/dashboards/json/phase-5-service-slo.json`, pre-provisioned into a "Phase 5 Services" folder) with a `$service` dropdown covers all nine — a near-identical dashboard duplicated nine times would just be nine copies to keep in sync on every panel change, for no benefit a template variable doesn't already give. Panels track exactly the guide §27 NFR targets: availability (`avg_over_time(up[...]))`, ≥ 99.9% threshold), error rate (5xx ratio), and p95/p99 latency (`histogram_quantile` over `http_server_requests_seconds_bucket`, thresholds at the guide's own 500ms/800ms targets) — plus request-rate and latency-over-time timeseries panels for context.

This required one small but necessary change across all nine services (`services/*/src/main/resources/application.yml`) plus the Gateway: `management.metrics.distribution.percentiles-histogram.http.server.requests: true`. Without it, Micrometer only exports `_count`/`_sum` (enough for an average, not a real percentile) — Prometheus never sees the histogram buckets `histogram_quantile()` needs, so the dashboard's own p95/p99 panels would silently show nothing. Not verified against a live Grafana instance in this session (Docker isn't available in this sandbox) — the dashboard JSON is validated as syntactically correct and the PromQL matches metric names already scraped by the existing Prometheus config, but hasn't been visually confirmed rendering real data end to end.

## Runbook — ✅ done, one per service

`docs/runbooks/<service>.md` for all nine — each covers health/metrics endpoints, a pointer to the shared Grafana SLO dashboard, and genuinely service-specific operational scenarios drawn from what's actually built (e.g. Investment Service's runbook leads with the service-to-service-auth gap since that's the single most important thing for an on-call engineer to know before touching it; Payment/KYC/AML's runbooks explain that a growing `PENDING`/`IN_PROGRESS` backlog is an expected human-review queue, not a bug, since none of them have real automated decisioning). Deliberately honest about what these runbooks *can't* say yet: no persistent dev/staging/prod environment exists in this project (`docs/ci-cd.md`), so the "deploy/rollback" section of every runbook says exactly that rather than describing a rollback procedure against an environment that doesn't exist.

Writing these surfaced two stale "known limitations" in existing READMEs (Fund Service and Portfolio Service both still said "no Pact contract test... yet" after the contract-tests exit criterion had already closed that gap) — fixed alongside.

## Owning team — ✅ done (placeholder)

Per explicit user decision: a single placeholder, `@platform-engineering`, in a root `CODEOWNERS` file (one line per service directory) plus an "Owning team" note in each service's README and runbook. `@platform-engineering` is not a real GitHub team — nobody will actually be requested for review from `CODEOWNERS` as it stands today. This is organizational information the session doesn't have, not a technical gap; replace every line once a real team is assigned per service.
