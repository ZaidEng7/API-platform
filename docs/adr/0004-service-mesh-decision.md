# ADR 0004: Service mesh decision

**Status:** Accepted — not adopted, revisit criteria below
**Date:** 2026-08-01

## Context

Phase 7 lists "Service mesh — only if mTLS/traffic management outgrows current setup" — again a decision item, not a default build. This ADR is that decision, and it directly follows on from [ADR 0001](0001-service-to-service-authentication.md), which already flagged the gap a mesh would otherwise close:

> "Does **not** solve mTLS/transport-level trust (guide §8.1's literal ask) — a compromised service still can't be distinguished from a compromised client secret holder at the network layer. That remains real infrastructure work, tracked separately, not solved by this ADR."

Guide §8.1 names mTLS (mesh or ingress-level) as the expected mechanism for service-to-service transport security; guide §12.1 separately names OAuth2 Client Credentials for application-level service-to-service auth (what ADR 0001 actually implemented). Those are two different layers, and this platform has deliberately only built the second one so far.

What this platform actually runs today:

- One generic Helm chart (`deployment/helm/service-chart`) reused across every service, deployed (in CI only, via `k8s-smoke-test`) to a single ephemeral `kind` cluster — never a real multi-node or multi-cluster deployment.
- Default-deny-all `NetworkPolicy` per environment namespace (`dev`/`test`/`staging`/`prod`), but `deployment/README.md` already documents that `kind`'s default CNI doesn't enforce `NetworkPolicy` in CI, so even that isn't proven end-to-end yet.
- No multi-region, no multi-cluster, no cross-cluster traffic requirements anywhere in this platform.
- The one real "traffic management" need that has come up — Phase 6's weighted canary between Customer Service and `crm-adapter` — was deliberately solved at the **application layer** (`CanaryWeightRegistry` + a plain proxying controller in the Gateway), specifically because a mesh's/Gateway's static traffic-split primitives (Spring Cloud Gateway's own `Weight` filter included) can't deliver the "instant rollback = flag off" property that demo needed. That precedent already shows this platform's traffic-management needs so far are better solved in application code than by adding mesh-level infrastructure.

A service mesh (Istio, Linkerd) would add automatic mTLS between pods and much richer traffic-management primitives (retries, timeouts, circuit-breaking, traffic splitting at the mesh layer) — but it's real, persistent infrastructure (sidecar injection, its own control plane, its own operational burden) that this platform has nowhere to run today (no persistent cluster exists at all outside CI's ephemeral `kind` cluster — see `docs/ci-cd.md`).

## Decision

Do not adopt a service mesh now. The application-level OAuth2 Client Credentials auth from ADR 0001 remains the only service-to-service security mechanism; the mTLS/transport-level gap that ADR flagged stays open and undecided at the mesh-vs-ingress-mTLS level.

**Revisit this decision if any of these become real:**

1. A **persistent, multi-node cluster** actually exists to run a mesh's control plane and sidecars on (today there is none — see `docs/ci-cd.md`'s "no persistent staging environment" gap).
2. **Multi-cluster or multi-region** deployment, where mesh-level cross-cluster service discovery/routing would solve a real problem app-level auth doesn't.
3. Traffic-management needs that genuinely **outgrow** what's been solved at the application layer so far (the Phase 6 canary's own precedent is that app-level solutions have handled this platform's actual needs adequately).
4. A compliance/audit requirement specifically mandates mTLS at the network layer (distinct from the application-level bearer-token auth ADR 0001 already provides) — this would be a §3.1-adjacent Compliance-driven decision, not a purely technical one.

## Consequences

- The mTLS gap ADR 0001 already flagged remains open. This is a known, accepted, documented gap — not an oversight.
- No new infrastructure, no sidecar-injection complexity, no mesh control-plane operational burden for a cluster topology (single ephemeral `kind` cluster in CI) that couldn't meaningfully use one yet.
- When a persistent cluster does exist, ingress-level mTLS termination (simpler, no mesh required) should be evaluated first, before a full mesh — a mesh is the heavier of the two guide §8.1 options and should only be chosen if ingress-level mTLS genuinely can't meet the need (e.g. pod-to-pod traffic within the cluster, not just at the edge).
