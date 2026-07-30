# Identity (Keycloak)

Config-as-code for the platform's Identity Provider (guide §5.1: Keycloak is the default; §12.1: OAuth2 + OIDC + JWT).

## Run locally

```bash
docker compose -f platform/identity/docker-compose.yml up -d
```

Keycloak starts on `http://localhost:8082` (chosen to avoid colliding with the Gateway's own `8080` and Customer Service's `8081`) and auto-imports `realm-export.json` (Keycloak's documented `start-dev --import-realm` mechanism — realm is created fresh each time the container starts with no existing data volume). Admin console: `http://localhost:8082/admin` (`admin` / `admin`, dev-only).

Discovery document: `http://localhost:8082/realms/company/.well-known/openid-configuration`

To make the Gateway actually enforce JWT auth against this realm:

```bash
export GATEWAY_JWT_ISSUER_URI=http://localhost:8082/realms/company
```

Unset (the default), the Gateway stays fully open — see `gateway/src/main/java/com/company/gateway/security/SecurityConfig.java`.

## What's defined

- **Realm**: `company`. Access tokens expire in 15 minutes (guide §12.1: "Access tokens ≤ 15 min").
- **Realm roles**: the guide's §12.2 RBAC list — `administrator`, `operations`, `compliance`, `portfolio-manager`, `customer-service`, `auditor`, `investor`, `partner`.
- **Clients**:
  - `gateway-portal` — public client, Authorization Code + PKCE (guide §12.1: portal/mobile flow). Redirect URIs currently point at `localhost:4200` for local Angular dev; update per environment.
  - `api-platform-services` — confidential client, Client Credentials grant, for service-to-service calls (guide §12.1). Secret in the realm export is an obvious local-dev placeholder, not a real credential — production secrets belong in Vault (guide §5.1), never in this file.
- **Test users**: `investor.test` / `test-password` (role `investor`) for local manual testing only.

## Known gaps / explicit TODOs

- **AD/LDAP federation** — the guide asks for a federation placeholder (§5.1: "federation with existing AD/LDAP"). Not configured because connection details (host, bind DN, user search base) don't exist yet — this is a Phase 1 input (see `docs/roadmap.md`, "Application inventory"). Once available, add a `components` → `org.keycloak.storage.UserStorageProvider` entry to `realm-export.json`; don't hand-configure it in a running realm and let it drift from source control.
- **MFA** — guide §12.1 requires MFA for admin portal and privileged operations; not yet configured (needs an OTP/WebAuthn policy decision).
- **No production secret management yet** — the client secret here is for local dev only. Real deployment needs it minted and stored in Vault, not committed.
