# ADR 0004: Service mesh decision

**Status:** Accepted — not adopted, revisit criteria below
**Date:** 2026-08-01
**Updated 2026-08-02:** named a concrete lighter-weight mechanism (Cilium CNI + WireGuard transparent encryption) to close the mTLS gap without a full mesh, after further research — no change to the core decision (mesh itself still not adopted).

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

Do not adopt a service mesh now. The application-level OAuth2 Client Credentials auth from ADR 0001 remains the only service-to-service security mechanism; the mTLS/transport-level gap that ADR flagged stays open.

**When a persistent cluster does exist**, close that gap with a CNI-level mechanism before reaching for a full mesh: specifically, **Cilium as the cluster's CNI, with WireGuard transparent encryption enabled** (`encryption: wireguard` in Cilium's Helm values). This encrypts all pod-to-pod traffic at the kernel/network layer automatically, cluster-wide, with no sidecars, no per-service code changes, and none of a mesh's control-plane operational burden — it's a CNI configuration choice, not a new platform to run. It gets this platform real wire-level encryption (closing guide §8.1's literal "never plaintext inside the cluster" ask) without paying for a mesh's traffic-management features this platform hasn't needed yet (see the Phase 6 canary precedent below). It does **not** provide mesh-style workload *identity* verification (mTLS certificates proving "this is actually the Portfolio Service," not just "this traffic is encrypted") — if that stronger guarantee is ever needed, that's the point to escalate to a full mesh (Istio/Linkerd), not before.

**Revisit toward a full mesh if any of these become real** (Cilium/WireGuard above should be tried first in every case):

1. **Workload identity**, not just encryption, is needed — verifying *which* service is on each end of a connection cryptographically, not just that the wire is encrypted.
2. **Multi-cluster or multi-region** deployment, where mesh-level cross-cluster service discovery/routing would solve a real problem neither app-level auth nor Cilium's single-cluster encryption does.
3. Traffic-management needs that genuinely **outgrow** what's been solved at the application layer so far (the Phase 6 canary's own precedent is that app-level solutions have handled this platform's actual needs adequately).
4. A compliance/audit requirement specifically mandates mesh-grade mTLS (distinct from both the application-level bearer-token auth ADR 0001 provides and the CNI-level encryption above) — this would be a §3.1-adjacent Compliance-driven decision, not a purely technical one.

## Consequences

- The mTLS gap ADR 0001 flagged gets closed cheaply (Cilium/WireGuard) once a persistent cluster exists, without waiting on a full mesh decision — a **persistent, multi-node cluster** is still the prerequisite for either option (today there is none — see `docs/ci-cd.md`'s "no persistent staging environment" gap).
- No mesh-level infrastructure, no sidecar-injection complexity, no mesh control-plane operational burden for needs a CNI-level encryption setting already covers.
- Choosing Cilium as the CNI is itself a real infrastructure decision (most Kubernetes distributions ship a different default CNI) — this ADR names it as the recommended path when a persistent cluster is actually being provisioned, not something to retrofit onto an existing cluster running a different CNI without its own evaluation.
