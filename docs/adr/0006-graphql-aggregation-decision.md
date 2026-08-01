# ADR 0006: GraphQL aggregation decision

**Status:** Accepted — not adopted, revisit criteria below
**Date:** 2026-08-01

## Context

Phase 7 lists "GraphQL aggregation (optional, portal-driven)" — the guide's own parenthetical already states the precondition: this only makes sense driven by a real portal's aggregation needs.

GraphQL aggregation earns its cost when a single consumer needs to combine data from several services into one response shape it controls (e.g. a Client Portal's "my dashboard" view pulling Customer + Portfolio + KYC status + recent Payments in one round trip, letting the frontend pick exactly the fields it needs instead of over/under-fetching from several REST calls).

This platform has:

- No real Web Portal, Mobile app, or Client Portal anywhere in this repo — guide §8.3's own SoR matrix names "Client Portal" as a separate, investor-facing read-copy destination for Portfolio positions, distinct from the internal/back-office Reporting Service this platform actually built (Reporting Service's own roadmap note is explicit: "Client Portal (investor-facing) isn't built anywhere in this platform").
- The Phase 6 canary demo and Phase 7 API-catalog work both explicitly note the same gap: no real consumer application exists to build a portal-facing aggregation layer for.
- Every existing REST endpoint across the nine business services is already purpose-shaped for its own bounded context (per guide §10-§11's REST style guide) — there's no observed over-fetching/under-fetching problem today, because there's no real frontend consuming these APIs to have that problem in the first place.

Building a GraphQL aggregation layer now would mean designing a schema against imagined consumer needs rather than a real one — precisely the kind of speculative work this platform has avoided (e.g. Reporting Service's own read models were built against the guide's actual SoR matrix grants, not invented use cases).

## Decision

Do not adopt GraphQL aggregation now.

**Revisit this decision when:**

1. A real portal-style consumer (Web Portal, Mobile, or Client Portal) actually gets built in this platform, per Phase 6's real consumer-migration work (not the Phase 6 starter/demo already done) or a genuinely new initiative.
2. That consumer has a demonstrated need to combine data from **multiple** services in one request, in a shape it wants to control — not just a convenience wrapper around one service's existing REST API.
3. The number/variety of such combined-view consumers grows enough that maintaining several bespoke REST aggregation endpoints (one per view) becomes more costly than adopting a query language built for exactly this.

## Consequences

- No new API surface, no new schema to design and maintain against non-existent consumer requirements.
- The nine business services' REST APIs remain the only public API surface, aggregated for discovery (not for data-shaping) via the Phase 7 API-catalog work already done at the Gateway.
- When a real portal consumer does emerge, its team is best positioned to define the actual GraphQL schema needed — this ADR deliberately leaves that design undone rather than guessing at it now.
