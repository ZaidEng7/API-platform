# Fund Management Adapter — TEMPLATE

This is a **template**, not a real integration — same story as `integration/crm-adapter` and `integration/onboarding-adapter`, see either README for the full rationale (Phase 1 Assessment, which would supply real legacy-system details, is still deferred). This is the third and last of Phase 4's priority adapters, and demonstrates the one thing the other two didn't: **caching**.

## Why caching, and why it's not just an optimization

Guide §9.1 lists caching alongside translation and resilience as an adapter responsibility. `crm-adapter` and `onboarding-adapter` didn't need it — but a fund's NAV (Net Asset Value) is a different kind of data: this fictional legacy system only republishes it **once a day, after market close**. Calling it more often than that doesn't get you fresher data, it just adds load to a system that has nothing new to say. Caching here isn't a shortcut — it's the design that actually matches the data's real freshness constraint, which is exactly the kind of thing guide §9.2 requires an adapter to document (rate limits, batch windows, downtime windows, **data freshness**).

`FundNavCacheConfig` sets the cache TTL from `fund-mgmt.nav-cache-ttl` (defaults to `PT30M` for local/demo convenience — a real integration should set this close to the actual batch-window length, once Phase 1 knows what that is).

**Why caching and resilience are split across two beans** (`FundNavProvider` for `@Cacheable`, `LegacyFundMgmtClient` for `@CircuitBreaker`/`@Retry`/`@Bulkhead`), not stacked as annotations on one method: Spring's proxy-based AOP — which is what makes both `@Cacheable` and resilience4j's annotations work — doesn't intercept a method calling another method on `this`. Two beans means the call from one to the other actually goes through the Spring-managed proxy, so a cache hit really does short-circuit before ever reaching the resilience layer or the network. `FundNavProviderCachingTest` proves this for real (a second call for the same fund never reaches WireMock at all), not just that the annotation is present.

## What's real vs. fictional

Same pattern as the other two adapters: the anti-corruption boundary shape, the caching design, the §9.4 resilience config (retry included — this is a read again, like `crm-adapter`, unlike `onboarding-adapter`'s write) are all real and reusable. `LegacyFundNavRecord` (the legacy shape — a scaled-integer NAV representation, `x10000`, which is actually a genuinely common convention in real legacy financial systems for avoiding floating-point rounding, not just a fabricated quirk this time), `legacy-fund-mgmt.base-url` (defaults to `http://localhost:9994`, nothing real), and the specific cache TTL / resilience numbers are fictional or dev-convenient — replace once Phase 1 supplies real details about the real legacy fund management system.

## Running it locally

```bash
mvn -pl integration/fund-mgmt-adapter -am spring-boot:run
curl http://localhost:8086/api/v1/funds/EQFND01/nav
```

Without a real (or stubbed) legacy backend listening on `legacy-fund-mgmt.base-url`, this fails fast per the resilience config — connect timeout, then retry, then eventually the 503 fallback (guide §9.4: "fail fast and honest").
