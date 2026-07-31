# Contract testing (Pact)

This directory holds consumer-driven contract tests (guide §20). Right now it contains one thing: a **template**, not a real contract.

## Why a template and not a real contract

There's no real typed API consumer in this codebase yet — the Gateway is a plain path-based reverse proxy, not code that calls another service's API and depends on its response shape. Pact tests express expectations a *real* consumer has of a *real* provider; fabricating one before a real consumer exists would just be asserting made-up behavior. So this scaffolds the tooling — broker, dependencies, CI wiring — plus one pair proving the whole pipeline actually works, clearly marked as a template.

## What's here

- `customer-consumer-example/` — a minimal HTTP client for `GET /api/v1/customers/{id}`, existing only so `CustomerServiceClientPactTest` has something to run the Pact consumer DSL against. Not a real service.
- The provider side lives in `services/customer-service`: `CustomerServicePactVerificationTest` (package `com.company.customer.contract`), verifying the real `CustomerController` (with `CustomerApplicationService` stubbed, so no live DB is needed) against whatever's published to the broker.
- `deployment/docker/pact-broker.yml` — the broker both sides talk to.

Both tests check response **shape only** (fields present, types line up) — not real business rules. That's intentional for a template; a real contract should assert whatever your actual consumer actually depends on.

## Running it locally

```bash
docker compose -f ../deployment/docker/pact-broker.yml up -d
mvn -pl customer-consumer-example test pact:publish   # from contracts/
mvn -pl ../services/customer-service test -Ppact-verification
```

## Replacing the template with a real pair

When a real consumer exists (e.g. the Web Portal, or a service that calls another service's API):

1. Add a new module here (or reuse the consumer's own module) with its own `@Pact`-annotated interactions describing what that consumer *actually* depends on.
2. Add a `@Provider("...")`-annotated verification test in the real provider service, tagged `@Tag("pact")` and excluded from its default surefire run the same way `CustomerServicePactVerificationTest` is (see that module's `pact-verification` Maven profile).
3. Wire both into `.github/workflows/ci.yml`'s `pact-contract-verification` job (or a new job, if there are enough pairs to warrant a matrix).
4. Delete `customer-consumer-example/` and `CustomerServicePactVerificationTest` once nothing depends on them as an example.
