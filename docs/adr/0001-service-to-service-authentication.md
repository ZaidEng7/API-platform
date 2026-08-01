# ADR 0001: Service-to-service authentication

**Status:** Accepted, implemented
**Date:** 2026-08-01

## Context

No service-to-service call anywhere in this platform carries a credential. This has been true since Fund Service's first outbound call (Phase 5 item 5) and has been re-flagged in every service built since that makes an outbound call to another one of our own services:

- Fund Service → `integration/fund-mgmt-adapter`
- Portfolio Service → Fund Service
- Investment Service → Customer Service, KYC Service, AML Service, Portfolio Service

Every one of those calls uses a plain `RestClient` with no `Authorization` header. This works today only because every receiving service's own `issuer-uri` is unset by default, which keeps `common-security`'s filter chain fully open (see `common-security`'s own README). The moment any receiving service's `issuer-uri` is configured for a real deployment, its `@PreAuthorize` method-security gates start denying every caller without a role-bearing authentication — including these internal calls, which would carry none. Spring Security's `AnonymousAuthenticationFilter` still populates a role-less principal for an unauthenticated request even when the filter chain itself permits it through, so this isn't a "maybe" — it's a guaranteed `403` the moment auth is turned on for real. This has never been exercised against a real secured deployment, only against WireMock/mocked test doubles that don't enforce security.

Guide §8.1 names mTLS as the expected mechanism ("Service-to-service calls go through mTLS — mesh or mutual TLS at ingress — never plaintext inside the cluster"). Guide §12.1 separately names OAuth2 Client Credentials as the platform's service-to-service auth grant. This platform doesn't provision a service mesh (no Istio/Linkerd anywhere in `deployment/`) or ingress-level mTLS termination — that's real infrastructure work, its own decision, not something to default into inside an ADR about application-level auth.

**What already exists, unused:** `platform/identity/realm-export.json` already defines a confidential client built for exactly this purpose:

```json
{
  "clientId": "api-platform-services",
  "name": "Service-to-service (confidential client)",
  "protocol": "openid-connect",
  "publicClient": false,
  "secret": "local-dev-only-not-a-real-secret",
  "standardFlowEnabled": false,
  "directAccessGrantsEnabled": false,
  "serviceAccountsEnabled": true
}
```

Backed by a service-account user (`service-account-api-platform-services`) with the `operations` realm role — that role is already broad enough to satisfy every downstream `@PreAuthorize` gate these internal calls hit today (`KycCheckController`, `ScreeningController`, `PortfolioController`, `FundController`, `CustomerController` all admit `operations`). This client was built in Phase 3, documented in `platform/identity/README.md` as "for service-to-service calls (guide §12.1)," and has never been wired into a single outbound `RestClient`. The gap isn't "we haven't decided how to do this" — it's "the mechanism already exists and nothing uses it."

## Decision

Use OAuth2 Client Credentials against the existing `api-platform-services` client for all service-to-service calls, via a shared component in `common-security` rather than duplicating token-fetching logic in each of Fund/Portfolio/Investment Service:

1. Add a `ServiceAuthTokenProvider` (or similar) to `shared/common-security` that requests a token from Keycloak's token endpoint (`{issuer-uri}/protocol/openid-connect/token`, `grant_type=client_credentials`) using `api-platform-services`'s client id/secret, and caches it until shortly before expiry (access tokens are 15 minutes per the realm's own `accessTokenLifespan`).
2. Add a `ClientHttpRequestInterceptor` (or a `RestClient.Builder` customizer) that attaches the cached token as a `Bearer` `Authorization` header — applied to every internal `RestClient` bean that calls another one of our own services (the 6 relationships listed above; **not** `fund-mgmt-adapter`, which is a legacy-system adapter, not a peer service, and has no auth of its own to speak of yet either — out of scope for this ADR).
3. Client secret sourced from an env var (`SERVICE_AUTH_CLIENT_SECRET` or similar), never hardcoded — the realm export's own secret is explicitly a local-dev placeholder (`platform/identity/README.md`: "production secrets belong in Vault, never in this file").

**Why Client Credentials over mTLS/mesh:** no new infrastructure is required — the identity provider, client, and service account already exist and are already used for user-facing auth in every service via `common-security`. mTLS/mesh remains the guide's own longer-term expectation (§8.1) and should be revisited once real cluster infrastructure exists (a mesh or ingress-level mTLS is a platform-wide decision, not something one service's `RestClient` config can provide on its own) — this ADR doesn't foreclose that, it picks the mechanism that's actually buildable with what this platform has today.

## Consequences

- Closes a real, repeatedly-documented gap: Fund/Portfolio/Investment Service's calls to Customer/KYC/AML/Portfolio Service and to each other stop returning `403` the moment `issuer-uri` is configured for a real deployment.
- One new shared dependency surface (`common-security`'s token provider) that every internal `RestClient` config in those three services needs to adopt — a mechanical but real change to `FundNavClientConfig`, `ExternalServiceClientConfig`, and Portfolio Service's own Fund Service client config.
- Does **not** solve mTLS/transport-level trust (guide §8.1's literal ask) — a compromised service still can't be distinguished from a compromised client secret holder at the network layer. That remains real infrastructure work, tracked separately, not solved by this ADR.
- Does **not** cover `fund-mgmt-adapter` (a legacy-system adapter, not a peer platform service) — that boundary's authentication, if any, is a Phase 4/legacy-integration concern, out of scope here.
- The service account's `operations` role is broad by design (matches every downstream gate that exists today) — as more granular per-relationship authorization needs emerge (e.g. "only Investment Service may call Portfolio Service's position-recording endpoint"), a single shared service account stops being precise enough, and per-service client identities become the natural next step. Not built now because no such need exists yet.

## Implementation

- `shared/common-security`: `ServiceAuthTokenProvider` (Client Credentials token fetch/cache, via `AuthorizedClientServiceOAuth2AuthorizedClientManager` — not hand-rolled) and `ServiceAuthRequestInterceptor` (`ClientHttpRequestInterceptor`, attaches the Bearer header). Both always registered as beans (same "always on, no-op until configured" pattern as `CommonSecurityAutoConfiguration`'s own `SecurityFilterChain`), configured via `platform.security.service-auth.client-id`/`client-secret` plus the service's own existing `spring.security.oauth2.resourceserver.jwt.issuer-uri`.
- Wired into the two relationships this ADR actually covers: Portfolio Service's `FundNavClient` → Fund Service, and Investment Service's four clients → Customer/KYC/AML/Portfolio Service.
- **Not** wired into Fund Service's own call to `fund-mgmt-adapter` — out of scope per the "Decision" section above (legacy-adapter boundary, not a peer-service call).
- No downstream role changes were needed: the `api-platform-services` service account already carries `operations`, which every affected `@PreAuthorize` gate already accepts.
- Tests (`ServiceAuthTokenProviderTest`, `ServiceAuthRequestInterceptorTest`) prove the no-op default, token fetch, caching (a second call doesn't re-hit the token endpoint), and header attachment against a WireMock stub standing in for Keycloak's token endpoint — not against a real running Keycloak instance.
