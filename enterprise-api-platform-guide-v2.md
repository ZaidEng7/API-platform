# Enterprise API Platform — Implementation Guide

## FinTech / Investment Management Platform

**Version:** 2.0 (validated and expanded)
**Technology Stack:** Java 21 + Spring Boot / Angular
**Audience:** Engineering, Architecture, DevOps, Security, QA
**Status:** Ready for team review and Phase 1 kickoff
**Date:** July 2026

---

# 1. Executive Summary

## 1.1 Purpose

This document defines the implementation strategy, architecture, standards, and roadmap for building a centralized Enterprise API Platform for the investment management ecosystem.

The company currently operates multiple independent systems, including (but not limited to):

* Customer Onboarding
* CRM
* Fund Management
* Portfolio Management
* KYC
* AML
* Digital Signature
* Payment Integration
* Reporting
* Document Management
* Risk Assessment
* Back Office
* Mobile Applications
* Client Portal

Most products either expose inconsistent API standards or have no APIs at all.

The objective is a unified API Platform that provides **secure, scalable, governed, and maintainable** access to all business capabilities.

## 1.2 What this platform is — and is not

| It IS | It is NOT |
|---|---|
| A single, governed entry point for all business APIs | A replacement for existing products |
| An integration and abstraction layer over legacy systems | A "big bang" rewrite |
| The foundation for future microservice migration | A monolithic middleware that owns all business logic |
| A security and observability enforcement point | A data warehouse or reporting engine |

## 1.3 Migration philosophy: Strangler Fig

Modernization is **incremental**. New consumers call the Gateway; the Gateway routes to either a new business service or a legacy adapter. Over time, functionality migrates behind the same stable API contracts without consumer impact. No consumer is ever forced into a coordinated "flag day" cutover.

---

# 2. Objectives

The API Platform shall:

1. Provide a **single entry point** for all APIs (internal, mobile, portal, partner).
2. **Standardize authentication and authorization** (OAuth2 / OIDC / JWT).
3. Hide legacy implementations behind stable, business-oriented API contracts.
4. Centralize **logging, monitoring, tracing, and audit**.
5. Enforce **governance**: naming, versioning, documentation, review.
6. Enable **future microservice migration** without consumer disruption.
7. Support **partner integrations** with quotas, API keys, and isolation.
8. Guarantee **regulatory auditability** (immutable audit trail, retention).
9. Reduce integration complexity and cost of change.
10. Provide **resilience by default** — no cascading failures across products.

**Measurable success criteria are defined in §32.**

---

# 3. Business Context

The company operates within the financial and investment sector. Typical capabilities:

Customer Onboarding · Fund Subscription · Redemption · Portfolio Management · Investment Advisory · CRM · KYC Verification · AML Screening · Digital Identity · Payment Processing · Reporting · Compliance · Document Management · Notifications · Accounting

Each system has different technology and ownership. The API Platform unifies these under one architecture and one set of standards.

## 3.1 Regulatory context (ACTION REQUIRED — confirm jurisdiction)

The platform must be built compliance-ready. Fill in the applicable regime before Phase 2 sign-off:

| Concern | Placeholder requirement | Jurisdiction mapping (examples) |
|---|---|---|
| Prudential / securities regulator | Regulator reporting APIs, access for auditors | Canada: OSFI / provincial securities (OSC); KSA: SAMA / CMA |
| AML / CTF | Screening audit trail, STR support, watchlist refresh SLAs | Canada: FINTRAC; KSA: SAFIU |
| Data protection / privacy | PII minimization, consent, right-to-access | Canada: PIPEDA; KSA: PDPL; EU clients: GDPR |
| Data residency | Where customer and transaction data may be stored/processed | Confirm cloud region constraints before infrastructure decisions |
| Records retention | Audit and transaction records retained ≥ N years, immutable | Typically 5–10 years; confirm exact figure |
| Card payments (if applicable) | PCI-DSS scope isolation — card data never enters the platform; use tokenization via PSP | PCI-DSS v4.0 |

**Rule:** compliance requirements are treated as architectural constraints, not features to add later.

---

# 4. High-Level Architecture

```
┌──────────────────────────────────────────────────────────────┐
│                     EXTERNAL CONSUMERS                       │
│  Mobile App │ Customer Portal │ Admin Portal │ Partners │    │
│                    Internal Applications                     │
└───────────────────────────┬──────────────────────────────────┘
                            │ HTTPS (TLS 1.2+)
                            ▼
┌──────────────────────────────────────────────────────────────┐
│                  EDGE: WAF / DDoS / LB                       │
└───────────────────────────┬──────────────────────────────────┘
                            ▼
┌──────────────────────────────────────────────────────────────┐
│                 API GATEWAY CLUSTER (stateless)              │
│  AuthN offload · Routing · Rate limiting · Validation ·      │
│  Correlation IDs · CORS · Versioning · API keys (partners)   │
└───────────────────────────┬──────────────────────────────────┘
                            │ mTLS (service mesh optional)
        ┌───────────────────┼────────────────────┐
        ▼                   ▼                    ▼
┌───────────────┐  ┌─────────────────┐  ┌──────────────────────┐
│ PLATFORM      │  │ BUSINESS        │  │ INTEGRATION LAYER    │
│ SERVICES      │  │ SERVICES        │  │ (Anti-Corruption)    │
│               │  │                 │  │                      │
│ Identity      │  │ Customer        │  │ CRM Adapter          │
│ (AuthN/AuthZ) │  │ Investment      │  │ Onboarding Adapter   │
│ Configuration │  │ Portfolio       │  │ Fund Mgmt Adapter    │
│ Audit         │  │ Fund            │  │ SOAP Adapter         │
│ Notification  │  │ KYC             │  │ DB Adapter           │
│ File          │  │ AML             │  │ Vendor Adapters      │
│ Search        │  │ Payment         │  │                      │
│ Workflow      │  │ Document        │  │        │             │
│ Reporting     │  │ CRM (future)    │  │        ▼             │
└───────────────┘  └─────────────────┘  │  EXISTING PRODUCTS   │
        │                   │           │  (SOAP, DB, vendor)  │
        └───────┬───────────┘           └──────────────────────┘
                ▼
┌──────────────────────────────────────────────────────────────┐
│  RabbitMQ (events) · Redis (cache) · PostgreSQL (per svc)    │
│  ELK/OpenSearch (logs) · Prometheus/Grafana · Jaeger (trace) │
└──────────────────────────────────────────────────────────────┘
```

**Corrections vs v1.0:**
* **Notification Service appears once** — it is a *platform service* (channel delivery: email/SMS/push). Business services publish domain events; Notification subscribes and delivers. It is not duplicated in the business layer.
* An explicit **edge layer** (WAF/DDoS/LB) precedes the Gateway.
* **Audit Service** is separated from Logging: audit is an immutable, regulator-facing business record; logging is operational telemetry. Different retention, different access controls.

---

# 5. Recommended Technology Stack

## 5.1 Backend

| Component | Choice | Rationale / Notes |
|---|---|---|
| Language | **Java 21 (LTS)** | Virtual threads for I/O-heavy adapters |
| Framework | **Spring Boot 3.x** | Team standard |
| Gateway | **Spring Cloud Gateway** | Java-native, team can extend filters in-house. *Alternative considered:* Kong/APISIX offer richer out-of-box partner portals — revisit only if Spring Cloud Gateway's partner key management proves limiting in Phase 7 |
| Security | Spring Security + **Spring Authorization Server** or **Keycloak** | **Default: Keycloak** (battle-tested OIDC, admin UI, federation with existing AD/LDAP). Build-your-own only if Keycloak cannot meet a hard requirement |
| Resilience | **Resilience4j** | Circuit breaker, retry, bulkhead, time limiter — mandatory in every adapter (§9.4) |
| Data | Spring Data JPA + **Flyway** | Versioned DB migrations mandatory; no manual schema changes |
| Validation | Jakarta Bean Validation | At API boundary and service layer |
| Mapping | MapStruct | Compile-time DTO mapping; no reflection mappers |
| API docs | springdoc-openapi (OpenAPI 3.1) | Generated from code, validated against design-first spec |

## 5.2 Frontend

* Angular (latest LTS) + Angular Material
* RxJS; **NgRx only where state complexity justifies it** (admin portal — yes; simple forms — no)
* TypeScript strict mode on
* Shared Angular workspace library for: auth interceptor, correlation-ID interceptor, error handling, API client generation from OpenAPI specs (`openapi-generator`)

## 5.3 Data & Infrastructure

| Concern | Choice | Notes |
|---|---|---|
| Database (new services) | **PostgreSQL 16** — one schema/instance **per service** | Database-per-service is the rule; see §8.2 |
| Database (legacy) | SQL Server — **accessed only via adapters** | No new consumer may connect to legacy DBs directly |
| Cache | Redis (Sentinel/Cluster) | Caching, rate-limit counters, distributed locks (sparingly) |
| Messaging | **RabbitMQ** (quorum queues) | Kafka is a Phase 7 decision, driven by event-streaming/replay needs — not adopted "by default" |
| Containers | Docker (distroless/temurin base images) | |
| Orchestration | Kubernetes | Namespaces per environment; NetworkPolicies enforced |
| Logs | ELK **or** OpenSearch — pick one in Phase 2, do not run both | |
| Metrics | Prometheus + Grafana | |
| Tracing | OpenTelemetry SDK → Jaeger/Tempo | **W3C Trace Context** propagation standard |
| Secrets | **HashiCorp Vault** or cloud KMS/Key Vault | No secrets in Git, env files, or images — ever |
| CI/CD | GitHub Actions **or** Azure DevOps — pick one in Phase 2 | |

---

# 6. Core Principles

Every service must be: **Stateless · Independently deployable · Containerized · Documented · Observable · Secure · Versioned · Testable · Resilient**

Plus three principles missing from v1.0:

1. **Design-first APIs** — the OpenAPI spec is authored and reviewed *before* implementation begins. Code-generated docs are validated against the approved spec in CI.
2. **Data ownership is explicit** — every business entity has exactly one system of record (§8.3). Everyone else holds read models or references.
3. **Failure is expected** — every remote call has a timeout, a retry policy (or an explicit decision not to retry), and a circuit breaker. "It will probably be up" is not a design.

---

# 7. API Gateway Responsibilities

The Gateway provides:

* AuthN offload: JWT validation (signature, expiry, issuer, audience), OAuth2/OIDC token relay
* Coarse-grained AuthZ (route-level scopes); fine-grained decisions stay in services
* Routing and API versioning
* Rate limiting and quotas (per client, per partner tier — Redis-backed)
* Request validation (size limits, content-type, basic schema)
* Correlation ID: generate `X-Correlation-Id` if absent; always propagate (W3C `traceparent` for tracing)
* CORS policy (explicit allow-list; never `*` in production)
* API key validation + IP allow-listing for partners
* Response header hygiene (strip internal headers, add security headers)
* Canary/weighted routing to support strangler-fig migration

**The Gateway must never contain business logic.** If a Gateway filter starts making business decisions, that logic belongs in a service.

---

# 8. Microservice Responsibilities & Data Architecture

## 8.1 Ownership

Each microservice owns: business logic, its database, validation, business rules, domain events, and its API contract.

* Services **never** access another service's database.
* Communication is **REST (synchronous)** or **events via RabbitMQ (asynchronous)** only.
* Service-to-service calls go through mTLS (mesh or mutual TLS at ingress) — never plaintext inside the cluster.

## 8.2 Database-per-service — and the legacy reality

New services get their own PostgreSQL schema/instance. Legacy SQL Server databases remain owned by legacy products; the **adapter is the only component permitted to touch them**, and adapters are read/write only within the legacy product's own sanctioned interfaces (stored procedures, vendor APIs) — not free-form SQL against another team's tables unless formally agreed and documented.

## 8.3 System-of-Record matrix (complete in Phase 1)

| Entity | System of Record (target) | Interim SoR (legacy) | Read copies allowed in |
|---|---|---|---|
| Customer / Party | Customer Service | Onboarding + CRM (conflict!) | Portfolio, CRM, KYC |
| KYC status | KYC Service | KYC product | Customer read model |
| Fund / NAV | Fund Service | Fund Management product | Portfolio, Reporting |
| Portfolio positions | Portfolio Service | Portfolio product | Reporting, Client Portal |
| Payment status | Payment Service | PSP + Back Office | Investment, Reporting |
| Documents | Document Service | DMS product | — (references only) |

**Phase 1 must resolve every "conflict!" cell.** Two systems both believing they own Customer is the single biggest integration risk in this program.

## 8.4 Consistency across services: Saga + Outbox

Multi-service business transactions (e.g., **fund subscription**: validate customer → KYC/AML check → reserve units → collect payment → confirm → notify) must NOT use distributed 2PC transactions. Instead:

* **Orchestrated saga** for flows with clear coordination needs (subscription, redemption). The owning service (Investment Service) drives the saga, persists saga state, and executes **compensating actions** on failure (release reservation, refund/void payment).
* **Transactional Outbox pattern** for reliable event publishing: domain change + outbox row committed in one local transaction; a relay publishes to RabbitMQ. No dual-write of "save to DB then publish" without outbox.
* All saga steps and event consumers must be **idempotent** (§12.3).
* Every saga has a **timeout and a dead-letter path** with operational alerting — a stuck subscription must page someone, not silently rot.

---

# 9. Integration Layer (Anti-Corruption Layer)

## 9.1 Pattern

Every legacy/vendor system gets its own **Adapter Service**:

```
Legacy CRM (SOAP / stored procs)
        ↓
   CRM Adapter  ← translation, resilience, caching, contract stability
        ↓
   Clean REST API (business language)
        ↓
      Gateway
```

The adapter is an **anti-corruption layer**: legacy data models, field names, and quirks stop at the adapter boundary. Upstream consumers see only the canonical business model.

## 9.2 Adapter rules

1. One adapter per legacy system (not per consumer).
2. Adapters are **thin**: translate, protect, cache. Business orchestration lives in business services.
3. Adapters expose the same API standards as any other service (OpenAPI, versioning, errors).
4. Adapters document the legacy system's constraints: rate limits, batch windows, downtime windows, data freshness.

## 9.3 Legacy write semantics

If a legacy system cannot participate in sagas (no compensation possible), the adapter must state so in its README, and flows using it must be designed as **confirm-then-execute** (do the irreversible legacy write last).

## 9.4 Mandatory resilience configuration (every adapter, every outbound call)

| Control | Default | Notes |
|---|---|---|
| Connect timeout | 2s | |
| Read timeout | 5s (10s max for known-slow legacy ops) | No unbounded waits |
| Retry | 2 retries, exponential backoff + jitter, **idempotent operations only** | Never blind-retry a payment POST |
| Circuit breaker | Open at 50% failure over sliding window 20 calls; half-open probe after 30s | Resilience4j |
| Bulkhead | Bounded concurrency per downstream | One slow legacy system must not exhaust threads |
| Fallback | Defined per endpoint: cached value, degraded response, or explicit 503 with Problem Details | "Fail fast and honest" beats hanging |

---

# 10. API Design Standards

## 10.1 Resource style

```
GET    /customers              — list (paginated)
GET    /customers/{id}
POST   /customers
PUT    /customers/{id}         — full replace
PATCH  /customers/{id}         — partial update (JSON Merge Patch)
DELETE /customers/{id}         — logical delete where regulation requires retention
```

* Business language only. Never expose table names (`/tbl_customer` ❌), internal IDs of legacy systems, or vendor field names.
* Plural nouns, kebab-case paths, camelCase JSON fields.
* Sub-resources for containment: `/customers/{id}/accounts`. Max two levels deep.
* Actions that don't map to CRUD use verb sub-resources: `POST /subscriptions/{id}/cancel`.

## 10.2 Pagination, filtering, sorting (mandatory on all collections)

```
GET /transactions?page=0&size=50&sort=createdAt,desc&status=SETTLED&fromDate=2026-01-01
```

* Default page size 20, max 200. Requests above max → 400.
* Response envelope includes `page`, `size`, `totalElements`, `totalPages`.
* Cursor-based pagination permitted for high-volume streams (document it in the spec).

## 10.3 Long-running & asynchronous operations

AML screening, report generation, bulk operations are **not synchronous**:

```
POST /aml/screenings            → 202 Accepted
    Location: /aml/screenings/{id}
GET  /aml/screenings/{id}       → { "status": "IN_PROGRESS" | "COMPLETED" | "FAILED", ... }
```

* Status polling + optional **webhook callback** for partners (signed with HMAC, retried with backoff, receiver must be idempotent).
* Never hold an HTTP connection open waiting on a legacy batch process.

## 10.4 Dates, money, identifiers

* Timestamps: ISO-8601 UTC (`2026-07-14T09:30:00Z`). No local times on the wire.
* **Money: `{ "amount": "1250.50", "currency": "SAR" }` — amount as string/decimal, never float.** ISO-4217 currency codes. This is non-negotiable in an investment platform.
* IDs: UUIDs (or ULIDs) for new entities. Legacy IDs are mapped inside adapters, not leaked.

## 10.5 API lifecycle & deprecation

* Breaking change ⇒ new major version (`/api/v2`). Additive changes (new optional fields) do not.
* A deprecated version gets: `Deprecation` + `Sunset` headers, an entry in the developer changelog, and a **minimum 6-month sunset window** (12 months for partner-facing APIs).
* Max **two major versions live** per API at any time.

---

# 11. Standard Response & Error Contract

## 11.1 Success

```json
{
  "success": true,
  "data": { },
  "meta": { "page": 0, "size": 20, "totalElements": 143 }
}
```

`meta` present only for collections.

## 11.2 Errors — RFC 7807 Problem Details (mandatory, not "where possible")

```json
{
  "type": "https://api.company.com/errors/validation-failed",
  "title": "Validation Failed",
  "status": 400,
  "detail": "Request contains 2 invalid fields",
  "instance": "/api/v1/customers",
  "correlationId": "9f3a7c1e-...",
  "timestamp": "2026-07-14T09:30:00Z",
  "errorCode": "CUST-4001",
  "errors": [
    { "field": "nationalId", "code": "INVALID_FORMAT", "message": "Must be 10 digits" }
  ]
}
```

* **Error codes are catalogued** per service (`CUST-xxxx`, `PAY-xxxx`) in a shared registry — support and partners resolve issues by code, not by parsing messages.
* Internal exceptions, stack traces, SQL fragments, and legacy system names **never** appear in responses.
* 4xx = caller's problem, 5xx = ours. Adapters translate legacy failures honestly (legacy down = 503 + Retry-After, not 500).

---

# 12. Security Architecture

## 12.1 Authentication

* **OAuth2 + OIDC + JWT** via central Identity Provider (Keycloak default, §5.1).
* Flows: Authorization Code + PKCE (portal, mobile), Client Credentials (service-to-service, partners), token exchange for on-behalf-of scenarios.
* Access tokens ≤ 15 min; refresh tokens rotated on use; revocation supported.
* MFA mandatory for admin portal and privileged operations.

## 12.2 Authorization

* **RBAC** roles: Administrator, Operations, Compliance, Portfolio Manager, Customer Service, Auditor, Investor, Partner.
* Roles/permissions are **configurable data, not code**. Services enforce fine-grained permissions; the Gateway enforces only coarse scopes.
* **Object-level authorization is mandatory**: an Investor sees *their* portfolio only. Every `GET /portfolios/{id}` must verify ownership — this is the #1 API vulnerability class (BOLA/IDOR).
* Segregation of duties for sensitive ops (e.g., large redemption approval requires a second role). Four-eyes checks are workflow rules, configurable by Compliance.

## 12.3 Idempotency (mandatory for all unsafe operations on money)

* `POST` endpoints that create financial effects (payments, subscriptions, redemptions, transfers) require an **`Idempotency-Key` header** (UUID, client-generated).
* Server stores key + request hash + response for ≥ 24h. Same key + same body ⇒ replay stored response. Same key + different body ⇒ 409.
* Event consumers deduplicate on event ID.

## 12.4 Data protection

* TLS 1.2+ everywhere external; **mTLS for service-to-service** traffic.
* Encryption at rest (DB, backups, object storage).
* **Field-level protection for sensitive data**: national ID, bank account/IBAN — encrypted or tokenized at the application layer; masked by default in API responses (`SA03 80** **** **** 1234`) unless the caller holds an explicit unmask permission (which is itself audited).
* PII minimization: services store only the fields they need; everything else is a reference to the system of record.

## 12.5 Platform security baseline

HTTPS only · input validation at boundary · parameterized queries only · OWASP Top 10 + **OWASP API Security Top 10** compliance · secrets in Vault with rotation (≤ 90 days, automated) · no hardcoded credentials · dependency scanning (Dependabot/Snyk) + container image scanning (Trivy) in CI · SAST in CI · DAST + annual penetration test before major releases · signed container images · Kubernetes NetworkPolicies default-deny.

---

# 13. Audit (distinct from logging)

Business-significant events are written to the **Audit Service** as immutable records:

* Who (user/client ID) · What (action, entity, before/after where applicable) · When (UTC) · Where (channel, IP) · Correlation ID.
* Auditable events include: login/logout, permission changes, customer data changes, KYC/AML decisions, subscription/redemption/payment lifecycle, document access, unmasking of sensitive fields, configuration changes.
* Append-only storage, retention per regulatory requirement (§3.1), access restricted to Auditor/Compliance roles and itself audited.
* Audit writes are **asynchronous via outbox** — audit must not add latency to the hot path, but must not be lossy either.

---

# 14. Logging Standards

Structured JSON logs. Every request logs: correlation ID, user/client ID, IP, endpoint, method, duration, status, service name, request/response sizes, exception class + message.

**Never logged:** passwords, tokens, full national IDs, full IBANs, card data, document contents. Masking is enforced by the shared logging library (§21), not by developer discipline.

Log levels: `ERROR` pages someone or ends in a dashboard; `WARN` means degraded; `INFO` is business flow; `DEBUG` is off in production.

---

# 15. Monitoring & Observability

Every service exposes: `/actuator/health`, readiness, liveness, Prometheus metrics (JVM, HTTP, connection pools, **business metrics** — subscriptions/min, screening backlog, saga failures, circuit-breaker state).

* **Tracing:** OpenTelemetry auto-instrumentation + manual spans around legacy calls; W3C Trace Context propagated end-to-end including through RabbitMQ headers.
* **SLOs per service** (defined in Phase 2, e.g., p99 latency < 800ms, availability 99.9%) with error-budget-based alerting.
* **Alerting policy:** every alert has an owner and a runbook link. Alerts without runbooks are deleted.
* Dashboards: one platform overview + one per service, standardized layout.

---

# 16. API Versioning

* URI versioning: `/api/v1`, `/api/v2`.
* Breaking changes (removing/renaming fields, changing types/semantics, tightening validation) ⇒ new major version.
* Additive changes ⇒ same version. Consumers must tolerate unknown fields (enforced in contract tests).
* Deprecation policy per §10.5.

---

# 17. Naming Standards

* Services: `customer-service`, `portfolio-service`, `crm-adapter` (adapters suffixed `-adapter`).
* Packages: `com.company.customer`, `com.company.integration.crm`.
* Kubernetes: same name as service; one Helm chart pattern for all.
* Queues/exchanges: `domain.entity.event` → `investment.subscription.confirmed` (see §22 event naming).
* Git repos: one repo per service **or** a structured monorepo — decide once in Phase 2 and never mix.

---

# 18. Repository / Folder Structure

```
api-platform/
├── gateway/
├── platform/
│   ├── identity/                # Keycloak config-as-code, realm exports
│   ├── audit-service/
│   ├── notification-service/
│   ├── configuration-service/
│   ├── file-service/
│   └── workflow-service/
├── services/
│   ├── customer-service/
│   ├── kyc-service/
│   ├── aml-service/
│   ├── fund-service/
│   ├── portfolio-service/
│   ├── investment-service/
│   ├── payment-service/
│   ├── document-service/
│   └── reporting-service/
├── integration/
│   ├── crm-adapter/
│   ├── onboarding-adapter/
│   ├── fund-mgmt-adapter/
│   └── <system>-adapter/
├── shared/
│   ├── platform-bom/            # dependency versions (Maven BOM)
│   ├── common-web/              # error handling, Problem Details, envelopes
│   ├── common-security/         # JWT, permission checks
│   ├── common-logging/          # structured logging + masking
│   ├── common-messaging/        # outbox, idempotent consumer, event envelope
│   └── common-test/             # test fixtures, Testcontainers helpers
├── contracts/                   # OpenAPI specs (design-first source of truth)
├── deployment/
│   ├── docker/
│   ├── helm/
│   └── environments/            # dev / test / staging / prod values
└── docs/
    ├── adr/                     # Architecture Decision Records
    ├── runbooks/
    └── onboarding/
```

Each service internally follows a consistent layering:

```
customer-service/
└── src/main/java/com/company/customer/
    ├── api/            # controllers, DTOs, mappers
    ├── application/    # use cases / orchestration
    ├── domain/         # entities, domain services, events
    ├── infrastructure/ # repositories, messaging, external clients
    └── config/
```

---

# 19. Shared Libraries

Published as versioned artifacts from `shared/` (internal Maven registry):

JWT & security helpers · global exception handling (Problem Details) · structured logging with masking · standard response envelope · validation utilities · outbox + idempotent-consumer support · OpenAPI configuration · Testcontainers base classes.

Rules: shared libraries contain **cross-cutting plumbing only — never business logic**; versioned semantically; a breaking library change must not force same-day upgrades on all services.

---

# 20. Development Standards

Every service ships with:

* Unit tests (JUnit 5, ≥ 80% line coverage on domain/application layers — coverage of getters doesn't count)
* Integration tests with **Testcontainers** (real PostgreSQL, RabbitMQ — no H2-only test suites)
* **Contract tests (Pact)**: consumer-driven contracts between Gateway/frontends and services, and between services. Broker verification blocks deploys on broken contracts. *This is the single most important addition for a 10+ service platform.*
* OpenAPI spec (design-first, in `contracts/`, code validated against it in CI)
* Dockerfile (multi-stage, non-root, pinned base image)
* Health checks, structured logging, config profiles (`dev`,`test`,`staging`,`prod`)
* Static analysis (SonarQube quality gate) + code coverage reports

## Coding rules

Constructor injection only · SOLID · composition over inheritance · no business logic in controllers · no DB access outside repositories · DTOs at API boundary (never expose JPA entities) · MapStruct for mapping · Flyway for every schema change · no `@Transactional` on controller methods · all external calls wrapped in Resilience4j.

---

# 21. Git & CI/CD

## Git

Trunk-friendly GitFlow-lite: `main` (production) · `develop` · `feature/*` · `release/*` · `hotfix/*`. PRs mandatory, ≥ 1 approving review (2 for `shared/` and `gateway/`), CI green before merge, conventional commits for changelog automation.

## Pipeline (every commit)

```
Build → Unit tests → Static analysis (Sonar gate) → SAST + dependency scan
     → Docker build + image scan (Trivy) → Integration tests (Testcontainers)
     → Contract verification (Pact broker) → Publish artifact
     → Deploy to dev (auto) → Deploy to test (auto) 
     → Deploy to staging (auto, smoke tests) → Deploy to prod (manual approval)
```

No production deployment without the full chain green. Rollback = redeploy previous image tag (DB migrations must be backward-compatible one version — expand/contract pattern).

---

# 22. Event & Messaging Standards (new section)

* Broker: RabbitMQ, quorum queues, publisher confirms on.
* **Event envelope** (mandatory): `eventId` (UUID), `eventType`, `occurredAt`, `correlationId`, `producer`, `schemaVersion`, `payload`.
* Naming: `<domain>.<entity>.<event-past-tense>` → `customer.kyc.approved`, `payment.transfer.settled`.
* Producers use the **outbox pattern** (§8.4). Consumers are **idempotent** (dedupe on `eventId`).
* Every queue has a **DLQ** with alerting and a documented replay procedure.
* Event schema changes follow the same compatibility rules as APIs: additive OK, breaking ⇒ new `eventType` version.

---

# 23. Environments, Data & Disaster Recovery (new section)

## Environments

| Env | Purpose | Data |
|---|---|---|
| dev | Integration of feature work | Synthetic |
| test | QA, automated E2E | Synthetic + anonymized |
| staging | Prod-like, performance & UAT | **Anonymized/masked production data only** — real PII never leaves prod |
| prod | Live | Real |

## Backup & DR

* PostgreSQL: PITR (WAL archiving), daily full backups, **restore drills quarterly** — an untested backup is a rumor.
* Targets (confirm with business): **RPO ≤ 15 min, RTO ≤ 4h** for core services; document per-service tiers.
* RabbitMQ mirrored/quorum across AZs; Redis with persistence for rate-limit state (or accept loss — decide explicitly).
* DR runbook per service; annual DR exercise.

## Capacity & performance

* Load tests (Gatling/k6) in staging before each major release; baseline: expected peak × 3.
* HPA on CPU + custom metrics (queue depth for consumers).

---

# 24. Governance & Ways of Working (new section)

* **API Design Review**: every new/changed OpenAPI spec is reviewed by the platform architecture group *before* implementation. Lightweight — 30 min, checklist-driven (naming, errors, pagination, security, versioning).
* **Architecture Decision Records (ADRs)**: significant choices (broker, gateway product, saga vs. choreography per flow) are recorded in `docs/adr/`. Future engineers get the *why*, not just the *what*.
* **Service ownership**: every service has a named owning team, on-call rotation, and a `CODEOWNERS` entry. "The platform team owns everything" does not scale past Phase 4.
* **API catalog**: start with the OpenAPI specs in `contracts/` + Swagger UI aggregation; graduate to a developer portal (Backstage or vendor) in Phase 7.

---

# 25. Implementation Phases & Roadmap

Durations assume a core platform team of 6–8 engineers + 1 architect + 1 DevOps, with product-team participation in adapter phases. Adjust after Phase 1 sizing. Phases 4 and 5 deliberately overlap.

## Phase 1 — Assessment (4–6 weeks)

Inventory every product: stack, owner, database, existing APIs, auth method, dependencies, consumers, third-party integrations, data entities held, batch windows, known constraints.

**Deliverables:** application inventory · API catalog (as-is) · dependency map · **System-of-Record matrix (§8.3) with all conflicts resolved** · integration priority ranking (business value × feasibility).

**Exit criteria:** SoR matrix signed off by product owners; top-5 adapter candidates agreed.

## Phase 2 — Standards & Governance (3–4 weeks, overlaps Phase 1)

Define and publish: API style guide · error code registry structure · security baseline · logging/tracing standards · event standards · SLO templates · documentation templates · tooling decisions (log stack, CI/CD platform, repo strategy, Keycloak vs. build).

**Deliverables:** Engineering Handbook v1 (this document, finalized) · API Style Guide · Security Baseline · ADRs for all Phase-2 decisions.

**Exit criteria:** standards ratified; jurisdiction/compliance table (§3.1) completed and signed by Compliance.

## Phase 3 — Platform Foundation (8–10 weeks)

Build: Gateway · Identity (Keycloak realm, clients, roles, federation) · shared libraries v1 · Audit Service · logging/metrics/tracing stack · Kubernetes environments (dev/test/staging/prod) · CI/CD pipeline template · one **walking-skeleton service** (Customer Service shell) proving the full path: portal → gateway → service → DB → event → audit → dashboards.

**Deliverables:** operational platform foundation with the walking skeleton in staging.

**Exit criteria:** an end-to-end authenticated request is traceable in Jaeger, logged in ELK, audited, and monitored in Grafana; pipeline deploys to staging automatically.

## Phase 4 — Legacy Integration (10–14 weeks, then continuous)

Build adapters for priority systems: **CRM, Customer Onboarding, Fund Management** first. Each adapter ships with contract tests, resilience config (§9.4), and legacy-constraint documentation.

**Deliverables:** priority adapters live behind the Gateway; unified REST interfaces.

**Exit criteria:** at least one real consumer (e.g., an internal app) using an adapter through the Gateway in production.

## Phase 5 — Business Services (16–24 weeks, overlaps Phase 4)

Build order (dependency-driven):

1. **Customer Service** (everything depends on Party data)
2. **KYC Service** + 3. **AML Service** (compliance gates for all money movement)
4. **Document Service** (KYC needs it)
5. **Fund Service** → 6. **Portfolio Service** → 7. **Investment Service** (subscription/redemption sagas live here)
8. **Payment Service** (idempotency + PSP integration)
9. **Reporting Service**

(Identity/AuthN was built in Phase 3 — it is platform, not a business service. Notification is platform, built in Phase 3–4.)

**Exit criteria per service:** design-reviewed spec · contract tests green with all consumers · SLO dashboard live · runbook written · owning team named.

## Phase 6 — Consumer Migration (8–12 weeks, per consumer)

Migrate Web Portal → Mobile → internal systems → partners onto the Gateway.

**Method:** strangler fig with **parallel run** — consumer calls new API, results compared against legacy path (shadow traffic) for a defined bake period; feature flags per module; weighted canary at the Gateway; instant rollback = flag off. Partner migration gets the 12-month deprecation window (§10.5).

**Exit criteria:** zero consumers with direct legacy access; legacy endpoints firewalled off.

## Phase 7 — Optimization (continuous)

Event streaming (Kafka decision) · distributed caching strategy · service mesh (only if mTLS/traffic management outgrows current setup) · workflow orchestration · GraphQL aggregation (optional, portal-driven) · developer portal + self-service partner onboarding · API analytics · AI-assisted documentation and anomaly detection.

---

# 26. Risks & Mitigations (new section)

| Risk | Impact | Mitigation |
|---|---|---|
| SoR conflicts unresolved (Customer owned by 2 systems) | Data corruption, sync loops | Phase 1 exit criterion; no adapter built for an entity without an SoR decision |
| Legacy systems can't handle adapter load | Outages in core products | Bulkheads + rate limits *toward* legacy; load-test adapters against legacy test envs |
| Gateway becomes a business-logic dumping ground | Unmaintainable chokepoint | Architecture review; "no business logic in gateway" enforced in PR review |
| Platform team becomes bottleneck | Delivery stalls in Phase 5 | Golden-path templates + paved-road CI so product teams self-serve |
| Partner-breaking changes | Reputational/regulatory damage | Contract tests + 12-month sunset policy + partner sandbox |
| Compliance requirements arrive late | Rework of audit/data design | §3.1 table completed in Phase 2, Compliance sign-off gate |
| Key-person dependency on legacy knowledge | Adapter quality | Pair legacy SMEs with adapter developers; constraints documented in adapter READMEs |

---

# 27. Non-Functional Requirements

| NFR | Target |
|---|---|
| Availability (core services) | ≥ 99.9% monthly (≈ 43 min downtime budget) |
| Latency | p95 < 500 ms, p99 < 800 ms for synchronous reads (excl. legacy-bound calls — documented per adapter) |
| Scalability | Horizontal, stateless; HPA configured |
| RPO / RTO | ≤ 15 min / ≤ 4 h (core); per-service tiers documented |
| Security | §12 baseline; annual pen test |
| Auditability | 100% of §13 event classes captured; retention per regulation |
| Backward compatibility | Two live major versions max; 6/12-month sunset |
| Maintainability | Sonar quality gates; ADRs; runbooks |
| Portability | Cloud-ready (Kubernetes); no proprietary lock-in in core path |

---

# 28. Documentation Requirements

Every service provides: architecture diagram · OpenAPI spec (design-first) · sequence diagrams for sagas/complex flows · README (purpose, owner, run locally) · configuration guide + environment variables · deployment guide · runbook (alerts → actions) · known limitations · error-code registry entries.

---

# 29. Future Enhancements

Developer Portal (Backstage/vendor) · self-service partner API keys · GraphQL Gateway · event streaming (Kafka) + schema registry · service discovery/mesh · centralized feature flags · multi-region deployment · workflow engine · AI-assisted documentation, monitoring, and anomaly detection.

---

# 30. Success Criteria

The implementation is successful when:

1. All products are reachable through a single, authenticated API Gateway.
2. AuthN/AuthZ centralized; zero consumers with direct legacy DB/API access.
3. Every business entity has one enforced system of record.
4. All money-moving APIs are idempotent and saga-protected with compensation paths.
5. Full observability: any request traceable end-to-end by correlation ID within 1 minute.
6. Audit trail complete and immutable, satisfying the confirmed regulatory regime.
7. CI/CD: commit → staging fully automated; prod deploys are one approval + one click; rollback < 15 min.
8. Contract tests protect every consumer-provider pair; no breaking change reaches production undetected.
9. Documentation and API catalog current (checked in CI, not by hope).
10. New business capability can be exposed as an API in < 2 weeks using the paved road.

---

# Conclusion

This platform is the company's strategic integration backbone. The design separates concerns cleanly — Gateway (traffic), business services (logic + data ownership), adapters (legacy protection) — so the organization modernizes incrementally while preserving existing investments. The additions in v2.0 (data ownership, sagas/idempotency, resilience, contract testing, audit, DR, governance, and a time-boxed roadmap with exit criteria) turn the v1.0 vision into a plan a team can execute from day one.

**Immediate next steps:**
1. Confirm regulatory jurisdiction and complete §3.1.
2. Ratify the Phase 2 tooling decisions (log stack, CI/CD platform, repo strategy, Keycloak).
3. Kick off Phase 1 inventory with the SoR matrix as the forcing function.
