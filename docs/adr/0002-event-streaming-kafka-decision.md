# ADR 0002: Event streaming (Kafka) decision

**Status:** Accepted — not adopted, revisit criteria below
**Date:** 2026-08-01

## Context

Guide §7's technology table already frames this explicitly: "Kafka is a Phase 7 decision, driven by event-streaming/replay needs — not adopted 'by default'." Phase 7 (Optimization) lists "Kafka decision (event streaming) — only if replay/streaming needs justify it" as its own checklist item, so this ADR is that decision.

RabbitMQ (quorum queues, `domain-events` topic exchange) has been this platform's only message broker since Phase 3, and every Phase 5 service publishes and/or consumes through it:

- All nine business services publish domain events via `common-messaging`'s outbox pattern (`OutboxEventStore`/`OutboxRelayPublisher`).
- Audit Service consumes everything (`"#"` binding) for the platform-wide audit trail.
- Reporting Service consumes `fund.#`/`portfolio.#`/`payment.#` to build three materialized read-model views (`DomainEventReportingListener`), each idempotent on `eventId` via `common-messaging`'s `IdempotencyGuard`.
- Customer Service's own KYC-status read model and Investment Service's saga steps are the other real consumer relationships that exist.

None of this has ever needed anything RabbitMQ doesn't already provide:

- **No replay requirement exists.** Reporting Service's materialized views are built by straightforward consume-and-upsert against live traffic — there's no scenario in this codebase where a consumer needs to reprocess historical events from an arbitrary point in time. If Reporting Service's tables were ever lost, the guide's own architecture doesn't describe rebuilding them from an event log — that's not a pattern this platform has adopted anywhere.
- **No long-retention requirement exists.** Every event's lifecycle ends at one of a small number of known consumers reacting once (idempotently) — nothing depends on messages surviving for days/weeks after being consumed.
- **No throughput pressure exists.** This is a synchronous-request-driven investment platform (subscriptions, KYC checks, payments) — order-of-magnitude event volume is nowhere near what would strain RabbitMQ's quorum queues.
- **No multi-consumer-group fan-out beyond what a topic exchange already does.** RabbitMQ's routing-key bindings (`fund.#`, `portfolio.#`, `payment.#`, `#`) already let multiple independent consumers each get their own copy of the events they care about — the core reason teams reach for Kafka's consumer-group model.

## Decision

Stay on RabbitMQ. Do not adopt Kafka.

**Revisit this decision if any of these become real:**

1. A consumer needs to **replay** historical events from an arbitrary point in time (e.g. rebuilding Reporting Service's read models from scratch instead of from a snapshot/backup).
2. Message **retention** needs to extend meaningfully past "until every current consumer has processed it" — e.g. a future analytics/ML pipeline that reads the same event stream on its own schedule, independent of the real-time consumers.
3. Real, measured **throughput** that RabbitMQ's quorum queues can't sustain (this has never been measured or approached in this platform).
4. A genuine need for Kafka-specific semantics (log compaction, exactly-once stream processing via Kafka Streams) that RabbitMQ's model can't express.

## Consequences

- No new infrastructure, no new operational burden (Kafka brokers, ZooKeeper/KRaft, schema registry) for a need that doesn't exist yet.
- `common-messaging`'s outbox/idempotency abstractions stay broker-agnostic in principle, but have only ever been implemented/tested against RabbitMQ — adopting Kafka later would still be real work, not a config flip.
- If one of the revisit criteria above is triggered by a single service (not platform-wide), the more likely outcome is a **second, purpose-built stream** for that one need (e.g. a dedicated Kafka topic just for replay-needing analytics) rather than a wholesale RabbitMQ replacement — this platform's existing request/response and saga patterns have no need to migrate off RabbitMQ themselves.
