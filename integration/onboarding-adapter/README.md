# Onboarding Adapter — TEMPLATE

This is a **template**, not a real integration — same story as `integration/crm-adapter`, see that module's README for the full rationale. This one's the write-side counterpart: `crm-adapter` demonstrates a read (§9.4's retry/circuit-breaker/bulkhead/timeout table in full); this one demonstrates a **write**, and specifically the part of §9 that only applies to writes.

## What's different here vs. crm-adapter

**§9.3 — legacy write semantics.** `LegacyOnboardingClient.submit()` deliberately has **no `@Retry`**. Per §9.4: "never blind-retry a payment POST" — the same logic applies to any non-idempotent write. If the legacy call times out, there's no way to know whether the application was actually created before the timeout; retrying risks a duplicate. Circuit breaker and bulkhead still apply — they protect the *caller* from a struggling downstream, they don't retry the write itself.

This fictional legacy system also has no compensation/cancel endpoint, so per §9.3 it can't participate in a saga. Any real flow built on this adapter needs to be **confirm-then-execute**: do this irreversible legacy write last, after every other, reversible step has already succeeded.

**Bidirectional translation.** `crm-adapter` only translates a response; this one translates a request too, including a genuinely fiddly legacy quirk (a bare `yyyyMMdd` string instead of an ISO-8601 date) — a more realistic example of what an anti-corruption layer actually earns its keep doing.

**A real bug this template found**: the default `RestClient` HTTP factory (`ClientHttpRequestFactoryBuilder.detect()`) picks the JDK client, which attempts HTTP/2-over-cleartext by default. Against WireMock's embedded Jetty server, a POST with a body got `RST_STREAM: Stream cancelled` — Jetty didn't like the h2c negotiation. Fixed by forcing HTTP/1.1 via Apache HttpClient5 (`.httpComponents()`), which is also just more realistic: a legacy system from this era is essentially guaranteed to be HTTP/1.1 only. Applied the same fix to `crm-adapter` for consistency, even though its GET-only client happened not to trip it.

## What's real vs. fictional

Same pattern as `crm-adapter`: the anti-corruption boundary shape, the resilience config, RFC 7807 error handling are all real and reusable. `LegacyOnboardingApplicationRequest`/`Record` (the legacy shapes), `legacy-onboarding.base-url` (defaults to `http://localhost:9998`, nothing real), and the specific resilience numbers are fictional — replace once Phase 1 supplies real details about the real legacy onboarding system.

## Running it locally

```bash
mvn -pl integration/onboarding-adapter -am spring-boot:run
curl -X POST http://localhost:8085/api/v1/onboarding-applications \
  -H 'Content-Type: application/json' \
  -d '{"fullName": "Ada Lovelace", "email": "ada@example.com", "dateOfBirth": "1990-03-05"}'
```

Without a real (or stubbed) legacy backend listening on `legacy-onboarding.base-url`, this fails fast — no retry, straight to the circuit breaker and then a 503 fallback. That's intended (guide §9.4: "fail fast and honest").
