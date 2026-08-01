# Contract testing (Pact)

Consumer-driven contract tests (guide §20) for every real internal REST relationship between our own services.

## Where the tests actually live

Unlike an earlier version of this directory (see git history), there's no separate `contracts/` module anymore — a fake standalone client only ever existed here because no real consumer existed yet. Now that real consumers exist, each Pact test lives directly in the service that's actually the consumer or provider:

| Pair | Consumer test | Provider verification test |
|---|---|---|
| Fund Service → fund-mgmt-adapter | `services/fund-service/.../contract/FundNavClientPactTest.java` | `integration/fund-mgmt-adapter/.../contract/FundMgmtAdapterPactVerificationTest.java` |
| Portfolio Service → Fund Service | `services/portfolio-service/.../contract/FundNavClientPactTest.java` | `services/fund-service/.../contract/FundServicePactVerificationTest.java` |
| Investment Service → Customer Service | `services/investment-service/.../contract/CustomerServiceClientPactTest.java` | `services/customer-service/.../contract/CustomerServicePactVerificationTest.java` |
| Investment Service → KYC Service | `services/investment-service/.../contract/KycCheckClientPactTest.java` | `services/kyc-service/.../contract/KycServicePactVerificationTest.java` |
| Investment Service → AML Service | `services/investment-service/.../contract/AmlScreeningClientPactTest.java` | `services/aml-service/.../contract/AmlServicePactVerificationTest.java` |
| Investment Service → Portfolio Service | `services/investment-service/.../contract/PortfolioPositionClientPactTest.java` | `services/portfolio-service/.../contract/PortfolioServicePactVerificationTest.java` |

Each pair asserts only what the real consumer's own client code actually depends on — response shape/status where the client parses the body, request/status only where it doesn't (see `PortfolioPositionClientPactTest`'s and `CustomerServiceClientPactTest`'s own Javadoc for concrete examples of this). Fund Service and Portfolio Service are each both a consumer and a provider in this set, so their own `pom.xml`s carry both `pact-consumer` and `pact-provider` dependencies.

- `deployment/docker/pact-broker.yml` — the broker every consumer/provider test talks to.
- Every provider verification test is tagged `@Tag("pact")` and excluded from each service's default `mvn test` run (see that service's own `pact-verification` Maven profile) — it needs the broker up and the matching consumer pact already published, which only the `pact-contract-verification` CI job guarantees.

## Running it locally

```bash
docker compose -f ../deployment/docker/pact-broker.yml up -d
mvn install -DskipTests   # populate the local reactor so cross-module pacts resolve

# One example pair — repeat per row in the table above:
mvn -pl services/fund-service test pact:publish
mvn -pl integration/fund-mgmt-adapter test -Ppact-verification
```

## Adding a new pair

When a new real consumer relationship appears (a service starts calling another service's API for the first time):

1. In the consuming service's own test tree, add a `@Pact`-annotated consumer test exercising its real REST client class (not a fake one) — see any file in the table above for the pattern.
2. In the providing service's own test tree, add a `@Provider(...)`-annotated verification test, tagged `@Tag("pact")`, with `@State` handlers matching the consumer's `.given(...)` text. If that service isn't a Pact provider yet, add `au.com.dius.pact.provider:spring7` (test scope) and the `pact-verification` Maven profile to its `pom.xml` (copy from `services/customer-service/pom.xml`).
3. Wire both into `.github/workflows/ci.yml`'s `pact-contract-verification` job.
