# CRM Adapter — TEMPLATE

This is a **template**, not a real integration. Phase 1 (Assessment) — which was supposed to produce real details about the legacy CRM (protocol, auth, rate limits, downtime windows) — was deferred, so there's nothing real to adapt yet. Rather than wait, this demonstrates the anti-corruption-layer pattern (guide §9) against a stub backend, so the pattern, the resilience config, and the CI wiring are all proven before a real system exists to point at.

## What's real vs fictional here

**Real, reusable as-is:**
- The anti-corruption boundary shape: `api/` (clean, business-language REST) talking through `legacy/` (translation + resilience) to an external system.
- The resilience config in `application.yml` — connect/read timeouts, retry, circuit breaker, bulkhead — matches guide §9.4's mandatory table exactly, and [`LegacyCrmClientResilienceTest`](src/test/java/com/company/crmadapter/legacy/LegacyCrmClientResilienceTest.java) proves it actually works (retries a transient failure, opens the circuit under sustained failure, short-circuits to a 503 once open) against a real embedded HTTP server (WireMock), not just that the annotations are present.
- The RFC 7807 error handling, `ApiResponse` envelope, structured logging — all `common-web`/`common-logging`, unchanged from the rest of the platform (guide §9.2: adapters follow the same API standards as any other service).

**Fictional, replace once Phase 1 happens:**
- [`LegacyCrmCustomerRecord`](src/main/java/com/company/crmadapter/legacy/dto/LegacyCrmCustomerRecord.java) — a made-up legacy shape (`CUST_ID`, `CUST_STATUS_CD`, `VIP_FLG`) standing in for whatever the real CRM actually returns.
- `legacy-crm.base-url` defaults to `http://localhost:9999` — nothing real is listening there.
- The specific resilience numbers (20-call sliding window, 50% failure threshold, 30s open-state wait) are the guide's own defaults, not numbers derived from how the real legacy CRM actually behaves.
- No legacy write semantics documented (guide §9.3) — this adapter only reads.

## Replacing the template with a real integration

1. Get the real legacy system's actual constraints from Phase 1 (or directly from whoever owns it): protocol, auth, rate limits, batch/downtime windows, data freshness. Document them here, in this section.
2. Replace `LegacyCrmCustomerRecord` with whatever the real system actually returns (SOAP envelope, stored-proc result shape, whatever it is).
3. Re-tune the §9.4 resilience numbers in `application.yml` against real observed behavior, not the guide's generic defaults.
4. If the legacy system can't participate in a saga (no compensation possible), state that here and design any flow using it as confirm-then-execute — do the irreversible legacy write last (guide §9.3).
5. Add real Pact contract tests once a real consumer depends on this adapter (see `contracts/README.md` for the same template-replacement story on that side).

## Running it locally

```bash
mvn -pl integration/crm-adapter -am spring-boot:run
curl http://localhost:8084/api/v1/crm-customers/42
```

Without a real (or stubbed) legacy backend listening on `legacy-crm.base-url`, that request will fail fast per the resilience config — connect timeout, then retry, then eventually the 503 fallback. That's the intended behavior, not a bug: "fail fast and honest" (guide §9.4).
