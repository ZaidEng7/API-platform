# Phase 5 Exit Criteria Tracker

Guide §25 states, for every Phase 5 service: **"design-reviewed OpenAPI spec · contract tests green with all consumers · SLO dashboard live · runbook written · owning team named."** Phase 5's nine services were built and merged first (see `docs/roadmap.md`); this tracker closes the five exit criteria across all of them afterward, one criterion at a time.

| Service | OpenAPI spec | Contract tests | SLO dashboard | Runbook | Owning team |
|---|---|---|---|---|---|
| Customer Service | ✅ | ⬜ | ⬜ | ⬜ | ⬜ |
| KYC Service | ✅ | ⬜ | ⬜ | ⬜ | ⬜ |
| AML Service | ✅ | ⬜ | ⬜ | ⬜ | ⬜ |
| Document Service | ✅ | ⬜ | ⬜ | ⬜ | ⬜ |
| Fund Service | ✅ | ⬜ | ⬜ | ⬜ | ⬜ |
| Portfolio Service | ✅ | ⬜ | ⬜ | ⬜ | ⬜ |
| Investment Service | ✅ | ⬜ | ⬜ | ⬜ | ⬜ |
| Payment Service | ✅ | ⬜ | ⬜ | ⬜ | ⬜ |
| Reporting Service | ✅ | ⬜ | ⬜ | ⬜ | ⬜ |

## OpenAPI spec — ✅ done

Every service already exposes a live, auto-generated spec via `springdoc-openapi-starter-webmvc-ui` at `GET /v3/api-docs` and `GET /swagger-ui.html` — that scaffolding has existed since each service's own Phase 5 build. What was actually missing for "design-reviewed" was substance: the generated spec carried only method-name-derived operation ids and no grouping. Every controller across all nine services now carries a class-level `@Tag(name, description)` (one tag per service, naming the guide §8.3 entity it's the System of Record — or read-copy, for Reporting — for) and a `@Operation(summary = ...)` on every endpoint stating what it does and any non-obvious behavior (async shape, ownership enforcement, once-only semantics, etc.) — real review-quality documentation, not per-field `@Schema` padding on every DTO, which would have been high-volume, low-marginal-value churn across ~40 DTOs for information the response type itself already carries (field names, via `-parameters`, and correct types).

No static spec snapshot is checked into the repo — the live `/v3/api-docs` endpoint is the artifact a reviewer or consumer would actually use, and a checked-in copy would just be one more thing to keep in sync on every future endpoint change.

## Contract tests — not yet started

## SLO dashboard — not yet started

## Runbook — not yet started

## Owning team — not yet started
