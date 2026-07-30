# API Gateway

Spring Cloud Gateway shell (guide §7). Routes `/api/v1/customers/**` to Customer Service, generates/propagates `X-Correlation-Id`, and enforces an explicit CORS allow-list (never `*`).

## Run locally

```bash
mvn -pl gateway -am spring-boot:run
```

Requires Customer Service running (`CUSTOMER_SERVICE_URI`, defaults to `http://localhost:8081`).

## Known limitations

- No AuthN offload / JWT validation yet — lands with Identity (Keycloak), still on the roadmap.
- No rate limiting / API key validation for partners yet.
- No canary/weighted routing yet (needed for Phase 6 strangler-fig migration).
