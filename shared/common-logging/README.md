# Common Logging

Structured JSON console logging with mandatory field masking (guide §14). Auto-discovered by Spring Boot — just add the dependency, no configuration needed:

```xml
<dependency>
  <groupId>com.company.platform</groupId>
  <artifactId>common-logging</artifactId>
</dependency>
```

## What it provides

- JSON logs via `logstash-logback-encoder`, MDC fields (e.g. `correlationId` from `common-web`'s `CorrelationIdFilter`) included automatically.
- **Masking, enforced at the encoder, not by developer discipline**:
  - Named fields always fully masked: `password`, `token`, `accessToken`, `refreshToken`, `secret`, `apiKey`, `authorization`, `nationalId`, `iban`, `cardNumber`, `cvv`.
  - Free-text log messages scanned for IBAN-shaped and card-PAN-shaped values and masked in place.
- `LOG_LEVEL_ROOT` / `LOG_LEVEL_APP` env vars control root and `com.company.*` log levels (default `INFO`).

## Usage gotcha: `v()` vs `kv()`

When logging a sensitive value as a structured argument, use `StructuredArguments.value(key, val)` (`v()`), **not** `.keyValue(key, val)` (`kv()`). `kv()` inlines `key=value` into the human-readable `message` text *in addition to* adding it as a JSON field — path masking only strips the dedicated field, so the raw value would still leak into `message`. `v()` adds the field without touching the message text, so masking fully suppresses it.

```java
// Wrong — "password=s3cr3t" leaks into the message field even though
// the "password" JSON field itself gets masked
log.info("login attempt {}", kv("password", secret));

// Right — value only appears as a maskable JSON field
log.info("login attempt", v("password", secret));
```

## Known limitations

- Value-regex masking for national IDs is intentionally not included as a standalone pattern (too generic a digit-shape to mask without false-positiving on order IDs, amounts, etc.) — always log `nationalId` as a **named field**, not string-concatenated into a message, so the path mask catches it.
- No file/rolling appender yet — console-only, expected to be captured by the container runtime and shipped to ELK/OpenSearch (Phase 3, not yet stood up).
