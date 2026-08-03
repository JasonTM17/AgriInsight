# Production Readiness

This is the control record for an external AgriInsight deployment. It is not a
claim that production is approved. The current verdict is **NO-GO** until every
row below has a real accountable owner, deadline, approval reference, and
passing evidence.

The latest live repository/workstation snapshot is
[the production GO/NO-GO report](../plans/260802-1646-external-production-readiness/reports/260803-0815-production-go-no-go-snapshot.md).
It records the current missing production environment, source protection,
license, target recovery prerequisites, owners, and deadlines without
fabricating any of them.

## Verified internal baseline

- `v0.4.0` was released from commit
  `616527dcc7f4a03720fb48e617f9310ab9614873` after exact-head CI
  [`30697294137`](https://github.com/JasonTM17/AgriInsight/actions/runs/30697294137)
  passed and protected image publication
  [`30697808763`](https://github.com/JasonTM17/AgriInsight/actions/runs/30697808763)
  completed.
- Four first-party images are published to Docker Hub and GHCR with semantic and
  full-SHA tags, SBOM/provenance, candidate and returned-digest scan/smoke, and
  digest parity. This is supply-chain evidence, not hosting approval.
- OIDC validation, tenant RBAC/RLS, Kafka replay/recovery, browser acceptance,
  safe backup/restore wrappers, and session-key rollover have source or hosted
  evidence. Their production operations are not inferred from local/demo gates.

## Required owner matrix

Use `UNASSIGNED — NO-GO` until an actual organization-approved owner is known.
Do not put secrets, raw logs, customer data, or private tokens in this file;
store only a safe ticket/change/approval reference.

| Control | Required decision and evidence | Current owner / deadline | Unlock criterion |
|---|---|---|---|
| Production OIDC and MFA | Issuer, audience, client registrations, access-token discriminator, privileged-user MFA assurance, redirect/logout URIs, exact CORS origins, key/secret rotation policy | UNASSIGNED — NO-GO | Target IdP login and authorization gate passes with documented assurance semantics |
| Kafka/broker operations | TLS/SASL, ACLs, topology/replication/min-ISR, retention, monitoring, alerting, on-call and rollback owner | UNASSIGNED — NO-GO | Target broker failure/recovery and tenant-isolation evidence pass |
| Hosting, hostname and TLS | Host controls, TLS certificate lifecycle, forwarded-host/cookie policy, ingress limits, deployment authority | UNASSIGNED — NO-GO | Digest-pinned target deployment is reachable only through approved TLS/host controls |
| Deployment change authority | Immutable manifest, verified CI/publication runs, approved Docker context, non-secret deployment identity, target deployment change approval, maintenance window, and rollback authority | UNASSIGNED — NO-GO | Supported release entrypoint validates then deploys the approved manifest in the target environment |
| Audit retention and alerting | Retention duration, protected audit store, failure alert receiver, on-call owner, deletion/legal-hold policy | UNASSIGNED — NO-GO | Successful-read and authorization-denial audit paths have retention and alert evidence |
| Backup and restore | RPO/RTO, retention, encrypted off-host destination, encryption key owner, restore operator, recurring drill schedule | UNASSIGNED — NO-GO | Current-schema timed clean restore and approved recurring drill evidence exist |
| Credential rotation | IdP client, database, broker, registry, web session-key and secret-manager rotation cadence/owner | UNASSIGNED — NO-GO | Rotation rehearsal preserves service availability and records no secret values |
| Observability and rollback | Metrics/log/trace destination, alert routes, service-level thresholds, rollback authority and previous-digest procedure | UNASSIGNED — NO-GO | Health, alert delivery, rollback and post-rollback verification pass in target environment |
| Registry visibility and package policy | Docker Hub/GHCR visibility, least-privilege token ownership and rotation, immutable semantic/full-SHA tag policy, paired-digest policy | UNASSIGNED — NO-GO | Release record proves approved Docker Hub/GHCR digest parity, no tag overwrite, and reviewer approval |
| License and legal | Root repository license and OCI license-label policy | UNASSIGNED — NO-GO | Legal decision is recorded and image labels/documentation match it |

The release workflow serializes approved publishes and fails closed if it cannot
establish that a semantic or full-SHA tag is absent. This is not a replacement
for registry-side immutability: the named registry owner must protect both
Docker Hub and GHCR from out-of-band tag rewrites before promotion is approved.

## Controlled-promotion evidence

The machine-checked record uses
[`deploy/production-promotion-evidence.template.json`](../deploy/production-promotion-evidence.template.json).
Copy the template outside the repository, replace every `REQUIRED` value with
an approved non-secret value, export the four selected image variables, and
set `AGRIINSIGHT_PRODUCTION_DOCKER_CONTEXT` to the approved target Docker
context. The `target` block must provide `docker_context`, the lowercase
SHA-256 fingerprint of that context's Docker endpoint, and the fixed Compose
project identity `agriinsight-release`. The validator rejects a context
mismatch before any Compose action starts; the entrypoint then verifies the
endpoint fingerprint and scopes every Compose command to that project. Then
run the supported release entrypoint:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/start-production-release-compose.ps1 `
  -EvidenceFile 'D:\secure-deployment\production-promotion-evidence.json' `
  -Mode Validate
```

The entrypoint rejects incomplete/expired owner records, non-production
environment, mutable/unapproved images, invalid release identity, missing
recovery/rollback fields, or an image mismatch. It also verifies local digest
labels, provenance/SBOM attestations, paired Docker Hub/GHCR semantic/full-SHA
tag parity, exact CI/publication workflow metadata, and release Compose
configuration. Deploy and redeploy-previous-digest rollback require the
dashboard, backend, analytics, and web services to be healthy and the pipeline
container to exit `0`. Approved disable-exposure rollback is the narrow
exception: it verifies the bound local target, skips GitHub/registry lookup,
runs `docker compose down` only for the fixed release project, and then
verifies that no project containers remain. When that project is already empty,
it returns `status=ALREADY_DISABLED`, not a new shutdown pass. A pass does not
replace the target-runtime tests below or external approval authenticity checks.

## Evidence package

Keep the approved package in protected operational storage. It should contain
safe references rather than copied secrets or raw tenant data.

```text
release-evidence/vX.Y.Z/
  manifest.json                 # validator input, tag, commit, four digests
  ci/                           # exact-head CI and reviewer approval
  promotion/                    # paired-registry scan, SBOM, provenance, parity, smoke
  runtime/                      # digest-pinned deployment, TLS, OIDC, broker, health
  recovery-operations/          # RPO/RTO approval, restore drill, alert/rollback evidence
  legal/                        # license and registry-visibility decisions
```

## GO / NO-GO rule

Issue **GO** only when every owner-matrix row is approved, the supported
release entrypoint passes against the exact deployment environment, and target
evidence
proves deployment, OIDC/MFA authorization, broker interruption/recovery,
observability/alert delivery, current-schema restore, rollback, security,
integration, and browser gates. Any absent, expired, failed, or unverified item
is **NO-GO** with its owner, deadline, and unlock criterion recorded.
