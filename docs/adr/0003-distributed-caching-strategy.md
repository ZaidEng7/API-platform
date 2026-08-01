# ADR 0003: Distributed caching strategy

**Status:** Accepted — strategy documented, not implemented yet
**Date:** 2026-08-01

## Context

Phase 7's checklist names this item "Distributed caching strategy" — a strategy, not necessarily an implementation, which matches what's actually true today: exactly one cache exists anywhere in this platform.

`integration/fund-mgmt-adapter`'s `FundNavProvider` (`FundNavCacheConfig`) caches NAV lookups via Caffeine, keyed by fund code, with a TTL matching the fictional legacy system's once-daily NAV republish window (guide §9.1/§9.2 — caching here isn't an optimization, it's what correctness against that refresh cadence actually requires; see that module's README). This cache is **per-instance**: if `fund-mgmt-adapter` runs as more than one pod, each replica keeps its own independent cache, populated independently, potentially serving a slightly different NAV snapshot than its siblings until each replica's own TTL expires.

That inconsistency is real but bounded and, so far, harmless:

- The legacy NAV feed itself only changes once a day — the worst case across replicas is "different pods might disagree on today's NAV for up to one refresh cycle," not an unbounded staleness problem.
- No other service in this platform caches anything today. Every other service (Customer, KYC, AML, Document, Fund, Portfolio, Investment, Payment, Reporting) reads/writes its own Postgres database directly per request — there's no second candidate for a shared cache to solve a real problem for.
- Nothing in this platform currently runs multiple replicas of anything in a way that's been observed to cause a correctness issue from this — Kubernetes HPA scaffolding exists (Phase 3) but no service has needed to scale beyond one replica for real traffic that doesn't exist yet.

Building a distributed cache (Redis) for a consistency problem that hasn't actually manifested — and that this platform's one existing cache already tolerates by design — would be exactly the kind of speculative infrastructure this project has avoided elsewhere (e.g. Fund Service's NAV refresh stays manual rather than scheduled, specifically to avoid solving for a timing need nothing yet depends on).

## Decision

**Strategy, not immediate build:** keep `fund-mgmt-adapter`'s Caffeine cache as-is (per-instance is an acceptable, documented trade-off for its one real use case). Adopt **Redis via Spring's Cache abstraction** (`spring-boot-starter-data-redis` + `@EnableCaching` against a `RedisCacheManager`, replacing `@Cacheable`'s underlying `CacheManager` bean only — no call-site changes needed in `FundNavProvider` or wherever else) as the platform's standard mechanism **once a real need for shared cache state across replicas actually exists** — for example:

1. A service scales to multiple replicas *and* a cached value's staleness window matters enough that per-replica divergence would cause a real bug (unlike NAV's once-daily tolerance today).
2. A second service needs to cache something that must stay consistent with what another service (or another replica of the same service) sees.
3. Cache warm-up cost becomes expensive enough that losing an in-memory cache on every pod restart/redeploy is itself a problem Redis's persistence would solve.

Redis is the recommended mechanism specifically because Spring's Cache abstraction makes the switch from Caffeine a `CacheManager` bean swap, not a rewrite of caching call sites — low cost to adopt exactly when needed, which is why standing it up preemptively isn't worth the added operational surface (a new stateful dependency in `deployment/docker`, a new Testcontainers dependency in tests) today.

## Consequences

- No new infrastructure added now. `deployment/docker` gains no new service; no module gains a new dependency.
- The next engineer who needs shared cache state has a documented, pre-agreed mechanism (Redis + Spring Cache abstraction) to reach for, instead of re-litigating the choice or reaching for something ad hoc.
- `fund-mgmt-adapter`'s existing Caffeine-based `FundNavProviderCachingTest` stays the reference test pattern until a real Redis consumer exists to prove the swap against (with a Testcontainers-backed Redis, matching this platform's established "prove it against the real thing" testing discipline — no mocked cache tests).
