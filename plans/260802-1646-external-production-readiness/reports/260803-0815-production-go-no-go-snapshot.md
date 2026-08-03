---
title: External production go/no-go snapshot
status: no-go
generated_at: '2026-08-03T09:46:15+07:00'
scope: AgriInsight v0.4.0 external production promotion
evidence_type: live read-only repository and workstation checks
---

# External production GO/NO-GO snapshot

## Decision

**NO-GO.** The repository release candidate has supply-chain evidence, but no
external production environment, accountable approvals, or current-schema
restore evidence exists. No deployment, rollback, or production image publish
was performed by this assessment.

## Verified evidence

| Control | Evidence observed | Result |
|---|---|---|
| Release source | CI run 30697294137: main, commit 616527dcc7f4a03720fb48e617f9310ab9614873, completed/success | Pass |
| Image publication | Run 30697808763: v0.4.0, same commit, completed/success | Pass |
| Registry workflow gate | release-images environment has required reviewer and branch policy | Partial: release-image gate only |
| Production deployment record | GitHub production-deployment query returned an empty list | Blocked: no deployment evidence |
| Production environment | GitHub environments list has assistant-provider-evaluation and release-images only | Blocked: no production environment |
| Source governance | GitHub main branch-protection query returned Branch not protected | Blocked |
| Legal | Repository API reported license null | Blocked |
| Local restore prerequisites | Docker Server 29.5.3 reachable; C drive 9.576 GiB WARN and D drive 19.926 GiB FAIL; no guarded database target variables set | Blocked: no safe drill target or capacity |

All GitHub observations were read live on 2026-08-03. The CI and publication
records prove internal release quality, not public hosting or operational
approval.

## External control register

No person, team, deadline, approval reference, or production target can be
inferred from repository configuration. The existing release-images reviewer
is evidence of a GitHub release gate only; it is not an assignment for the
controls below.

| Control | Observed accountable owner / deadline | Missing approval or evidence | Unlock criterion |
|---|---|---|---|
| Source and deployment governance | UNASSIGNED / no deadline recorded | Protected main policy, protected production environment, target change authority, maintenance window | Authorized owner enables reviewed source/environment controls and signs a deployment change |
| Production OIDC and MFA | UNASSIGNED / no deadline recorded | Issuer, audience, client registrations, MFA assurance, origins, logout/redirect policy, rotation policy | Target login and authorization test passes with documented assurance |
| Kafka/broker operations | UNASSIGNED / no deadline recorded | TLS/SASL, ACLs, topology, replication/min-ISR, retention, alerting, on-call | Target interruption and recovery test proves tenant isolation |
| Hosting and TLS | UNASSIGNED / no deadline recorded | Host, DNS, certificate lifecycle, ingress, forwarded-host/cookie policy | Digest-pinned target is reachable only through approved host/TLS controls |
| Audit retention and alerting | UNASSIGNED / no deadline recorded | Retention, protected audit destination, receiver, legal-hold/deletion policy | Read and authorization-denial events retain and alert in the target |
| Backup and restore | UNASSIGNED / no deadline recorded | RPO/RTO, encrypted off-host destination, key owner, operator, recurring schedule | V30-or-newer timed clean restore and approved recovery policy |
| Credential rotation | UNASSIGNED / no deadline recorded | IdP, database, broker, registry, session-key, secret-manager owner/cadence | Rotation rehearsal preserves service availability without secret disclosure |
| Observability and rollback | UNASSIGNED / no deadline recorded | Metrics/log/trace destination, thresholds, alert routes, rollback authority | Health, alert delivery, redeploy-previous-digest and post-rollback tests pass |
| Registry policy | UNASSIGNED / no deadline recorded | Docker Hub/GHCR token owner, visibility, rotation, registry-side immutable tag policy | Both registries prevent out-of-band tag rewrites and retain paired digest evidence |
| License and legal | UNASSIGNED / no deadline recorded | License selection and OCI labeling policy | Legal decision is recorded and image labels/documentation agree |

## Recovery preflight

The checked-in recovery path validates checksum, minimum schema version, empty
target, role/RLS gates, Flyway validation, and report binding. Focused
validation accepts a V30 fixture and rejects V29, a tampered backup, or a run
without explicit confirmation. The recovery contract currently has 52 focused
passing tests across release, promotion, and restore controls, including the
dedicated `127.0.0.1` endpoint allowlist, global per-target mutex, checksum
locking, no-overwrite publication, and reparse-point preflight. It is not a
timed production restore result.

Remote staging restore remains blocked until an approved TLS provider contract
supplies certificate verification for both libpq and Flyway JDBC; the checked-in
drill intentionally fails closed to literal IPv4 loopback until then.

The retained historical local dumps are Phase 7 artifacts with Flyway schema
values `missing`, `9`, and `19`; none is V30-or-newer. They cannot be relabeled
or reused as current-schema drill evidence.

The actual workstation guard now fails: D is below its 20 GiB floor (19.926 GiB)
and C is below its 10 GiB warning threshold (9.576 GiB). A read-only inventory
found approximately 3.9 GiB of workspace temporary build/test cache under
`artifacts/_tmp`, including 3.4 GiB of stale editor-extension cache; no cache,
container, backup, or database data was removed by this assessment. Do not
lower the guard threshold or create a database target until capacity is restored.

## Required next actions

1. The responsible organization assigns a named accountable owner and deadline
   to every row in the external control register, then supplies protected
   approval references.
2. The repository owner protects main and creates a protected production
   environment; the release reviewer must not be assumed to own production
   operations.
3. Restore C and D disk guard PASS (currently approximately 0.5 GiB and 5.1
   GiB short of warning thresholds respectively), then hosting and platform
   owners provide a non-production, isolated target with approved credentials
   for a V30 restore drill.
4. Recovery owner approves RPO/RTO, encryption/key custody, off-host
   retention, restore operator, and drill schedule before any production
   recovery claim.
5. After all approvals are valid, use the controlled promotion entrypoint and
   collect deployment, OIDC, broker, recovery, observability, rollback,
   security, integration, and browser evidence.

## Unresolved questions

- Which named owner and deadline apply to each external control?
- Which provider/target is approved for hosting, OIDC, Kafka, off-host backup,
  observability, and secret rotation?
- What license and Docker Hub/GHCR visibility policy are approved?
