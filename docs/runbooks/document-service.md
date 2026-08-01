# Document Service Runbook

Target System of Record for "Documents" (guide §8.3: read copies "— references only"). Metadata-only — stores an opaque `storageReference` pointer, never file bytes; no real DMS/object-store integration exists behind it. Port 8086.

**Owning team:** Platform Engineering (placeholder — see `docs/phase-5-exit-criteria.md`).

## Health & metrics

- `GET /actuator/health`.
- Grafana → "Phase 5 Services" folder → **Phase 5 Service SLOs**, `$service = document-service`.
- Prometheus job name: `document-service`.

## Common scenarios

- **`storageReference` points nowhere.** Expected risk of the metadata-only design: this service never validates that `storageReference` resolves to a real object in a real DMS, because no real DMS is wired in yet. A dangling reference is a data-quality issue at the caller (whoever supplied the reference on upload), not something this service can detect on its own.
- **`409 DOC-4090`.** A document was already verified or rejected, and someone tried to review it again — document review is one-shot, same pattern as KYC/AML decisions.
- **`VERIFIED`/`REJECTED` review backlog.** No automatic verification exists — a human Compliance reviewer drives this via `POST .../verify` / `POST .../reject`. KYC Service is the natural eventual consumer of `customer.document.*` events (guide: "Document Service (KYC needs it)"), but that integration isn't built yet, so nothing currently blocks on this backlog automatically.
- **Service won't start.** Check Postgres/RabbitMQ connectivity first.

## Deploy / rollback

No persistent dev/staging/prod environment exists in this project yet (`docs/ci-cd.md`). Once one does: `helm rollback document-service <previous-revision>`, or redeploy the previous image tag from `ghcr.io/<owner>/api-platform-document-service`.
