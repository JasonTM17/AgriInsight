# Production Readiness

This is the control record for an external AgriInsight deployment. It is not a
claim that production is approved. The current verdict is **NO-GO** until every
row below has a real accountable owner, deadline, approval reference, and
passing evidence.

Promotion evidence is machine-checked from the `format_version: 3` contract.
Older v2 manifests are rejected, and placeholder text such as
`UNASSIGNED — NO-GO` is a documentation-only marker for unresolved control
ownership. It is never valid evidence.

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

Every approval row must carry all of these fields before GO is possible:

- `owner`
- `approval_ref`
- `approved_at_utc`
- `due_at_utc`
- `unlock_criterion`
- `rollback_responsibility`

| Control | Evidence key | Required decision and evidence | Owner | Approval ref | Approved at UTC | Due at UTC | Unlock criterion | Rollback responsibility | State |
|---|---|---|---|---|---|---|---|---|---|
| Production OIDC and MFA | `oidc` | Issuer, audience, clients, token discriminator, privileged-user MFA assurance, redirect/logout URIs, exact CORS origins, and key/secret rotation policy | UNASSIGNED — NO-GO | REQUIRED | REQUIRED | REQUIRED | Target IdP login and authorization gate passes with documented assurance semantics | REQUIRED | NO-GO |
| Kafka/broker operations | `broker` | TLS/SASL, ACLs, replication/min-ISR, retention, monitoring, alerting, on-call, and rollback ownership | UNASSIGNED — NO-GO | REQUIRED | REQUIRED | REQUIRED | Target broker failure/recovery and tenant-isolation evidence pass | REQUIRED | NO-GO |
| Hosting, hostname and TLS | `hosting` | Host controls, certificate lifecycle, forwarded-host/cookie policy, ingress limits, and deployment authority | UNASSIGNED — NO-GO | REQUIRED | REQUIRED | REQUIRED | Digest-pinned target deployment is reachable only through approved TLS/host controls | REQUIRED | NO-GO |
| Deployment change authority | `deployment` | Immutable manifest, exact CI/publication runs, approved Docker context and identity, change approval, maintenance window, and rollback authority | UNASSIGNED — NO-GO | REQUIRED | REQUIRED | REQUIRED | Supported release entrypoint validates then deploys the approved manifest in the target environment | REQUIRED | NO-GO |
| Audit retention and alerting | `audit_retention` | Retention duration, protected audit store, failure receiver, on-call owner, deletion policy, and legal hold | UNASSIGNED — NO-GO | REQUIRED | REQUIRED | REQUIRED | Successful-read and authorization-denial audit paths have retention and alert evidence | REQUIRED | NO-GO |
| Backup and restore | `recovery` | RPO/RTO, retention, encrypted off-host destination, key owner, restore operator, and recurring drill schedule | UNASSIGNED — NO-GO | REQUIRED | REQUIRED | REQUIRED | Current-schema timed clean restore and approved recurring drill evidence exist | REQUIRED | NO-GO |
| Credential rotation | `credential_rotation` | IdP, database, broker, registry, web session-key, and secret-manager rotation cadence and ownership | UNASSIGNED — NO-GO | REQUIRED | REQUIRED | REQUIRED | Rotation rehearsal preserves service availability and records no secret values | REQUIRED | NO-GO |
| Observability and rollback | `observability` | Metrics/log/trace destination, alert routes, service thresholds, rollback authority, and previous-digest procedure | UNASSIGNED — NO-GO | REQUIRED | REQUIRED | REQUIRED | Health, alert delivery, rollback and post-rollback verification pass in target environment | REQUIRED | NO-GO |
| Registry visibility and package policy | `registry` | Docker Hub/GHCR visibility, least-privilege token ownership/rotation, immutable tags, and paired-digest policy | UNASSIGNED — NO-GO | REQUIRED | REQUIRED | REQUIRED | Release record proves approved Docker Hub/GHCR digest parity, no tag overwrite, and reviewer approval | REQUIRED | NO-GO |
| License and legal | `license` | Root repository license and OCI license-label policy | UNASSIGNED — NO-GO | REQUIRED | REQUIRED | REQUIRED | Legal decision is recorded and image labels/documentation match it | REQUIRED | NO-GO |

The release workflow serializes approved publishes and fails closed if it cannot
establish that a semantic or full-SHA tag is absent. This is not a replacement
for registry-side immutability: the named registry owner must protect both
Docker Hub and GHCR from out-of-band tag rewrites before promotion is approved.

## Controlled-promotion evidence

The machine-checked record uses
[`deploy/production-promotion-evidence.template.json`](../deploy/production-promotion-evidence.template.json).
Copy the v3 template outside the repository, replace every `REQUIRED` value
with an approved non-secret value, export the four selected image variables,
and set `AGRIINSIGHT_PRODUCTION_DOCKER_CONTEXT` to the approved target Docker
context. Do not reuse v2 manifests; the validator rejects them. The `target`
block must provide `docker_context`, the lowercase SHA-256 fingerprint of that
context's Docker endpoint, and the fixed Compose project identity
`agriinsight-release`. The validator rejects a context mismatch before any
Compose action starts; the entrypoint then verifies the endpoint fingerprint
and scopes every Compose command to that project. Then run the supported
release entrypoint:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/start-production-release-compose.ps1 `
  -EvidenceFile 'D:\secure-deployment\production-promotion-evidence.json' `
  -Mode Validate
```

The entrypoint rejects incomplete or expired owner records, v2 manifests,
non-production environment, mutable or unapproved images, invalid release
identity, missing recovery or rollback fields, or an image mismatch. It also
verifies local digest labels, provenance/SBOM attestations, paired Docker
Hub/GHCR semantic/full-SHA tag parity, exact CI/publication workflow metadata,
and release Compose configuration. Deploy and redeploy-previous-digest
rollback require the dashboard, backend, analytics, and web services to be
healthy and the pipeline container to exit `0`. Approved disable-exposure
rollback is the narrow exception: it verifies the bound local target, skips
GitHub/registry lookup, runs `docker compose down` only for the fixed release
project, and then verifies that no project containers remain. When that project
is already empty, it returns `status=ALREADY_DISABLED`, not a new shutdown
pass. A pass does not replace the target-runtime tests below or external
approval authenticity checks.

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
