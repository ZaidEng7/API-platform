# ADR 0007: Developer portal + self-service partner onboarding decision

**Status:** Accepted — interim step done, full scope deferred, revisit criteria below
**Date:** 2026-08-01

## Context

Phase 7 lists "Developer portal (Backstage or vendor) + self-service partner onboarding" as one item. The guide itself (line "API catalog: start with the OpenAPI specs in `contracts/` + Swagger UI aggregation; graduate to a developer portal (Backstage or vendor) in Phase 7") already frames this as two stages, and only the first has been done: the Gateway now aggregates all nine business services' `/v3/api-docs` into one Swagger UI (`/swagger-ui.html`) — see `gateway/README.md`'s "API catalog" section.

The second stage — a full developer portal (Backstage or a vendor product) plus self-service partner onboarding — has the same problem GraphQL aggregation does ([ADR 0006](0006-graphql-aggregation-decision.md)): it's built to serve **partners**, and this platform has no real partner ecosystem:

- No partner-facing consumer exists anywhere in this repo — guide §10.5's 12-month partner-deprecation policy and Phase 6's "Migrate partners" checklist item are both still unstarted, for the same reason Phase 6 overall is a starter/demo: no real external consumer exists to migrate or onboard.
- `gateway/README.md`'s own "Known limitations" already states: "No rate limiting / API key validation for partners yet" — there has never been partner-facing traffic to validate or rate-limit.
- A full Backstage instance is a separate Node.js/TypeScript application with its own Postgres database and (typically) GitHub integration for its software catalog — a materially different tech stack from this platform's all-Java services, for a catalog need the lighter Swagger UI aggregation already serves adequately at this platform's current size (nine services, one internal audience).

Building either a full Backstage deployment or a partner self-service onboarding flow (API key issuance, partner identity management, rate-limit tiers) now would mean designing for partners that don't exist, the same speculative-build pattern avoided in every other ADR in this set.

## Decision

- **Developer portal**: the Swagger UI aggregation already delivered (Phase 7's first item) remains the interim developer-facing catalog. Do not stand up Backstage or a vendor portal now.
- **Self-service partner onboarding**: not built now. When a real partner integration exists, recommend reusing the OAuth2 Client Credentials pattern [ADR 0001](0001-service-to-service-authentication.md) already established (mint a partner-specific confidential Keycloak client, scoped to that partner's own realm role) rather than inventing a separate API-key system — this keeps exactly one machine-to-machine auth mechanism in the platform instead of two.

**Revisit this decision when:**

1. A **real partner integration** is actually being onboarded (not hypothetical) — that's the forcing function for both halves of this item.
2. The number of services/consumers grows enough that Swagger UI aggregation's flat dropdown (currently ten entries: nine services + Gateway) stops being a usable catalog on its own — a real signal to graduate to Backstage's richer catalog/ownership/lifecycle metadata.
3. Partner-facing traffic materializes, at which point rate limiting and the partner auth mechanism above become real, sequenced work (rate limiting itself is a separate, still-open Gateway known-limitation, not solved by this ADR either).

## Consequences

- No new Node.js/TypeScript stack, no new partner-identity data model, no new rate-limiting infrastructure for consumers that don't exist.
- The interim Swagger UI aggregation (already shipped) continues to serve internal developer discovery needs adequately at this platform's current scale.
- The path to a real partner mechanism is pre-decided (reuse ADR 0001's Client Credentials pattern) so the first real partner integration doesn't have to re-litigate the auth-mechanism choice — only implement it for a real client.
