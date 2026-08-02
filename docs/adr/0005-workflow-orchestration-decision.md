# ADR 0005: Workflow orchestration engine decision

**Status:** Accepted — not adopted, revisit criteria below
**Date:** 2026-08-01
**Updated 2026-08-02:** sharpened the revisit trigger (2nd multi-step saga) and pre-named the recommended engine (Temporal) after further research — no change to the core decision.

## Context

Phase 7 lists "Workflow orchestration engine" as an optimization item, with no stated precondition — but the only saga this platform has ever built shows why one isn't needed yet.

Investment Service (Phase 5 item 7) implements the guide's own named saga steps ("validate customer → KYC/AML check → reserve units → collect payment → confirm → notify") without distributed 2PC and without a workflow engine:

- Steps 1-3 (validate customer, KYC/AML check, reserve units) run **synchronously inside the initiating `POST`** — all three downstream calls are this platform's own fast REST APIs, not a legacy batch process, so there's no need to persist intermediate saga state between them.
- Only "await payment" — the one genuinely long-running, asynchronous step — gets durable state, a timeout, and a dead-letter path, via `SubscriptionTimeoutJob` (which simply reuses `OutboxRelayPublisher`'s own `@Scheduled` polling mechanism, no new infrastructure).
- `POST /{id}/cancel` (the guide's own exact example endpoint) is the one compensating action, and it's simple: no separate inventory hold exists to release, so cancelling *is* the full compensation.
- The whole saga is one entity (`Subscription`) with a small state machine (RESERVED → AWAITING_PAYMENT → CONFIRMED/CANCELLED/TIMED_OUT), not a multi-entity, multi-service choreography that would benefit from a visual workflow designer or a dedicated orchestration runtime (Temporal, Camunda, AWS Step Functions, etc.).

A workflow orchestration engine earns its cost when sagas have many async steps, need human-task queues, need cross-saga visibility/ops tooling, or need compensating actions complex enough that hand-rolled state machines become error-prone. None of that describes Investment Service's one saga, and no second saga exists anywhere in this platform (redemption, the natural mirror-image flow, hasn't been built — see Investment Service's own README/roadmap note) to make a stronger case.

Standing up a workflow engine (its own database, its own worker processes, a new SDK dependency, new operational surface) to orchestrate one two-state async step would be exactly the kind of infrastructure-ahead-of-need this platform has avoided elsewhere.

## Decision

Do not adopt a workflow orchestration engine now. Keep the current pattern — synchronous steps where possible, durable state + scheduled timeout + dead-letter only for the genuinely async step, hand-written compensating actions — as the platform's standard saga approach.

**Concrete revisit trigger: the platform's 2nd genuinely multi-step transactional saga.** Investment Service's subscription flow is the first; redemption (its natural mirror-image, not yet built) is the obvious candidate for the second. One saga can always be argued as a special case not worth new infrastructure for — a *second* one, independently arriving at similar durable-state/timeout/compensation needs, is the real signal that this is a recurring platform concern rather than a one-off. The broader conditions that would also justify adopting one on their own:

1. A saga grows to genuinely need **multiple durable async steps** (not just one "await X" step), where a hand-rolled state machine across several timeout/dead-letter jobs starts becoming its own maintenance burden.
2. A saga needs a **human-task queue** (e.g. a compliance officer's manual decision as a durable, resumable step with its own SLA/escalation) beyond what a simple `PENDING`-until-reviewed status field already provides (KYC/AML/Document Services' existing pattern).
3. Operations needs **cross-saga visibility/tooling** — a dashboard of in-flight sagas, retry/replay controls, etc. — beyond what querying a service's own database directly already gives today.

**If/when adopted, the recommended engine is Temporal** — it's the best fit for a JVM/Spring Boot shop specifically because workflows are written as plain Java code (not a separate DSL/BPMN designer like Camunda, and not tied to a single cloud vendor like AWS Step Functions), which keeps the same "workflow logic lives in the codebase, reviewed like any other code" property this platform's hand-rolled sagas already have — adopting it would change *how* durability/retries/compensation are implemented, not the team's whole way of working.

## Consequences

- No new infrastructure, no new SDK/runtime dependency, no new operational surface for a coordination problem that doesn't exist at this platform's current saga complexity.
- `SubscriptionTimeoutJob`'s pattern (reuse the existing outbox relay's `@Scheduled` mechanism rather than adopting a dedicated scheduler/workflow runtime) remains the template for any future single-async-step saga — including redemption, unless redemption itself is the trigger that justifies Temporal.
- If a second saga does arrive and needs real orchestration-engine capability, adopting Temporal becomes its own follow-up ADR at that point (worker deployment topology, its own Postgres-backed persistence, SDK adoption across whichever services get involved) — this decision doesn't preclude it, it just declines to build ahead of a demonstrated need and pre-names the engine so that choice doesn't need re-litigating then.
