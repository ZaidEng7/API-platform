# Enterprise API Platform — Delivery Plan

Derived from `enterprise-api-platform-guide-v2.md` §25 (Implementation Phases & Roadmap), expanded into actionable todos. Checkboxes track progress; update as we go.

**Workflow:** one branch per phase, push when phase deliverables are complete, merge to `main` only after CI is fully green, then delete the branch.

**Status legend:** ✅ done · 🔶 in progress · ⬜ not started · 🧑‍💼 needs Zaid/business/compliance input (not something code can produce)

---

## Phase 0 — Repo Bootstrap (done, on `main`)

- [x] Create folder structure per §18 (gateway, platform, services, integration, shared, contracts, deployment, docs)
- [x] `.gitignore`, git init, GitHub remote (`https://github.com/ZaidEng7/API-platform.git`)
- [x] Initial commit pushed to `main`

---

## Phase 1 — Assessment (guide: 4–6 weeks)

Inventory every existing product (stack, owner, DB, APIs, auth, dependencies, consumers, integrations, data entities, batch windows, constraints).

**Branch:** `phase-1/assessment`

- [ ] 🧑‍💼 Application inventory — list every existing product in scope (Onboarding, CRM, Fund Mgmt, Portfolio, KYC, AML, Digital Signature, Payment, Reporting, DMS, Risk, Back Office, Mobile, Client Portal): owner, stack, DB engine, existing API surface, auth method
- [ ] Draft inventory template/spreadsheet structure for the above (I can produce the template; Zaid/product owners fill in real data)
- [ ] 🧑‍💼 As-is API catalog — document what each product currently exposes (if anything)
- [ ] Draft dependency-map template (which systems call which)
- [ ] **System-of-Record matrix (§8.3)** — draft the matrix skeleton from the guide (Customer/Party, KYC status, Fund/NAV, Portfolio positions, Payment status, Documents)
- [ ] 🧑‍💼 Resolve the "conflict!" cell — Customer/Party is claimed by both Onboarding and CRM; needs a business decision on which becomes system of record
- [ ] 🧑‍💼 Integration priority ranking (business value × feasibility) — needs product/business input
- [ ] **Exit criteria:** SoR matrix signed off by product owners; top-5 adapter candidates agreed

## Phase 2 — Standards & Governance (guide: 3–4 weeks, overlaps Phase 1)

**Branch:** `phase-2/standards-governance`

- [ ] API Style Guide (naming, versioning, pagination, error format) — draft from §10–§11 of the guide
- [ ] Error code registry structure (`CUST-xxxx`, `PAY-xxxx` pattern) — scaffold `contracts/error-codes/`
- [ ] Security Baseline doc — draft from §12
- [ ] Logging/tracing standards doc — draft from §14–§15
- [ ] Event standards doc — draft from §22
- [ ] SLO templates — draft from §15/§27
- [ ] Documentation templates (README, runbook, ADR) — scaffold in `docs/`
- [ ] Tooling decisions to ratify: log stack (ELK vs OpenSearch — pick one), CI/CD platform (GitHub Actions vs Azure DevOps — pick one), repo strategy (per-service vs monorepo), Keycloak vs build-your-own
- [ ] 🧑‍💼 **Regulatory jurisdiction table (§3.1)** — blocking item flagged by Zaid's boss; needs Compliance confirmation (regulator, AML/CTF regime, data protection regime, data residency, retention years, PCI-DSS scope)
- [ ] **Exit criteria:** standards ratified; §3.1 table completed and signed by Compliance

## Phase 3 — Platform Foundation (guide: 8–10 weeks)

**Branch:** `phase-3/platform-foundation`

- [x] Shared `platform-bom` (Maven BOM) — Spring Boot 3.x, Java 21, Resilience4j, MapStruct, Flyway, springdoc-openapi versions pinned
- [x] **Walking-skeleton Customer Service** — layered Spring Boot shell (api/application/domain/infrastructure), `POST`/`GET /api/v1/customers`, RFC 7807 error handling, Flyway baseline migration, `/actuator/health`, springdoc OpenAPI UI
- [x] Gateway shell (Spring Cloud Gateway) — routes to Customer Service, correlation-ID filter (generate/propagate `X-Correlation-Id`), explicit CORS allow-list
- [x] `shared/common-web` — Problem Details error handling (`ApiException` + `GlobalExceptionHandler`), `ApiResponse`/`PageMeta` envelope, `CorrelationIdFilter` (MDC), auto-configured via Spring Boot autoconfiguration; customer-service refactored to depend on it
- [x] `shared/common-security` — JWT resource-server auto-config (inactive until issuer-uri configured), `KeycloakRealmRoleConverter`, `CurrentUser` helper, `@EnableMethodSecurity`
- [x] `shared/common-logging` — JSON logging (`logstash-logback-encoder`), mandatory masking of named sensitive fields + IBAN/PAN-shaped values in free text, auto-discovered `logback-spring.xml`; wired into customer-service
- [x] `shared/common-messaging` — `OutboxEventStore`/`OutboxRelayPublisher` (outbox pattern, §8.4), `IdempotencyGuard` (dedupe on eventId, §22), `EventEnvelope<T>`. Not yet wired into customer-service — no domain event to publish yet, that lands when a service actually needs one
- [x] `shared/common-test` — `AbstractPostgresIntegrationTest`/`AbstractMessagingIntegrationTest` (Testcontainers singleton-container pattern). Wired into customer-service (real Postgres + Flyway + full HTTP round-trip via MockMvc) and gateway (real routing + correlation-ID propagation against a stub backend, no Docker needed there)
- [x] Identity — `platform/identity/realm-export.json` (realm `company`, §12.2 RBAC roles, `gateway-portal` public client for Auth Code+PKCE, `api-platform-services` confidential client for Client Credentials), `docker-compose.yml` for local Keycloak, AD/LDAP federation documented as a Phase 1-blocked TODO. Gateway JWT enforcement wired (`SecurityConfig`, inactive until `GATEWAY_JWT_ISSUER_URI` is set) — verified for real in CI (`identity-smoke-test` job boots Keycloak, confirms realm import + token issuance + role mapping, and that the Gateway actually rejects unauthenticated requests once pointed at it)
- [x] Audit Service — durable quorum queue on `domain-events` (routing key `#`), dedupe via `common-messaging`'s `IdempotencyGuard`, append-only `audit_events` table, `GET /api/v1/audit-events` restricted to `AUDITOR`/`COMPLIANCE` via `@PreAuthorize` (§13). No real producer exists yet, so the integration test publishes directly to prove consume→persist→dedupe→authz end to end. Along the way, fixed the same "no custom SecurityFilterChain → Spring Boot default lockdown" trap in `common-security` that the Gateway hit, and fixed `common-web`'s catch-all exception handler swallowing `AccessDeniedException` as a 500 instead of 403 — both now have regression tests
- [ ] Logging/metrics/tracing stack — building incrementally, verified via CI each time (Docker not available in the local dev sandbox):
  - [x] Metrics: `deployment/docker/observability.yml` (Prometheus + Grafana, Prometheus datasource pre-provisioned), `micrometer-registry-prometheus` wired into gateway/customer-service/audit-service, `/actuator/prometheus` added to the permit-all list in both `common-security` and gateway's `SecurityConfig`. `metrics-smoke-test` CI job proves a real metric actually flows end-to-end into Prometheus, not just that the endpoint exists
  - [ ] Tracing: OpenTelemetry → Jaeger
  - [ ] Logging: ELK or OpenSearch (Phase 2 tooling decision still unratified — defaulting to OpenSearch for its permissive license; flag for Phase 2 sign-off)
- [ ] Kubernetes environments scaffolding (dev/test/staging/prod namespaces, NetworkPolicies default-deny)
- [ ] CI/CD pipeline template (`.github/workflows/` or Azure Pipelines, per Phase 2 decision): build → unit tests → Sonar gate → SAST/dependency scan → Docker build + Trivy scan → integration tests → Pact contract verification → publish → deploy dev/test/staging (auto) → prod (manual approval)
- [ ] **Exit criteria:** an authenticated request is traceable end-to-end in Jaeger, logged in ELK/OpenSearch, audited, and visible in Grafana; pipeline auto-deploys to staging

## Phase 4 — Legacy Integration (guide: 10–14 weeks, then continuous)

**Branch:** `phase-4/legacy-integration` (or one branch per adapter once underway)

- [ ] CRM Adapter (`integration/crm-adapter`) — anti-corruption layer, resilience config per §9.4, legacy-constraints README
- [ ] Onboarding Adapter (`integration/onboarding-adapter`)
- [ ] Fund Management Adapter (`integration/fund-mgmt-adapter`)
- [ ] Contract tests (Pact) for each adapter
- [ ] **Exit criteria:** at least one real consumer using an adapter through the Gateway in production

## Phase 5 — Business Services (guide: 16–24 weeks, overlaps Phase 4)

Build order is dependency-driven — do not reorder without an ADR:

- [ ] 1. Customer Service (Party data — everything depends on this)
- [ ] 2. KYC Service
- [ ] 3. AML Service
- [ ] 4. Document Service (KYC needs it)
- [ ] 5. Fund Service
- [ ] 6. Portfolio Service
- [ ] 7. Investment Service (subscription/redemption sagas, §8.4)
- [ ] 8. Payment Service (idempotency §12.3, PSP integration)
- [ ] 9. Reporting Service
- [ ] **Exit criteria per service:** design-reviewed OpenAPI spec, contract tests green with all consumers, SLO dashboard live, runbook written, owning team named

## Phase 6 — Consumer Migration (guide: 8–12 weeks per consumer)

- [ ] Migrate Web Portal (strangler fig + shadow traffic + canary)
- [ ] Migrate Mobile
- [ ] Migrate internal systems
- [ ] Migrate partners (12-month deprecation window applies, §10.5)
- [ ] **Exit criteria:** zero consumers with direct legacy access; legacy endpoints firewalled off

## Phase 7 — Optimization (continuous)

- [ ] Kafka decision (event streaming) — only if replay/streaming needs justify it
- [ ] Distributed caching strategy
- [ ] Service mesh — only if mTLS/traffic management outgrows current setup
- [ ] Workflow orchestration engine
- [ ] GraphQL aggregation (optional, portal-driven)
- [ ] Developer portal (Backstage or vendor) + self-service partner onboarding
- [ ] API analytics, AI-assisted docs/anomaly detection

---

## Immediate next actions (from guide's own "Immediate next steps")

1. 🧑‍💼 Confirm regulatory jurisdiction and complete §3.1 (Compliance)
2. 🧑‍💼 Ratify Phase 2 tooling decisions (log stack, CI/CD platform, repo strategy, Keycloak vs build)
3. Kick off Phase 1 inventory with the SoR matrix as the forcing function

Items marked 🧑‍💼 need Zaid, product owners, or Compliance — not something to code around. Everything else is buildable now.
