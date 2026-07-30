# Common Web

Shared cross-cutting web plumbing for synchronous (Spring MVC) business services — not used by the reactive Gateway, which has its own correlation-ID filter.

Add the dependency and it self-registers via Spring Boot auto-configuration — no manual `@Import` needed:

```xml
<dependency>
  <groupId>com.company.platform</groupId>
  <artifactId>common-web</artifactId>
</dependency>
```

## What it provides

- **`CorrelationIdFilter`** — reads `X-Correlation-Id` (set by the Gateway) into MDC for every log line; generates one if missing (e.g. direct/local calls).
- **`GlobalExceptionHandler`** — RFC 7807 Problem Details (§11.2) for `ApiException`, validation errors, and any unhandled exception (never leaks stack traces).
- **`ApiException`** — throw with an `HttpStatus` + per-service error code (e.g. `CUST-4041`) + detail message; the handler does the rest.
- **`ApiResponse<T>` / `PageMeta`** — standard success envelope (§11.1).

## Known limitations

- No Kubernetes-style header allowlist/redaction yet — pairs with `shared/common-logging` once that lands.
