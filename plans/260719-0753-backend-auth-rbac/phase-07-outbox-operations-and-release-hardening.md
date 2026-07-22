---
phase: 7
title: "Outbox operations, verified images, and release hardening"
status: pending
priority: P1
effort: "4-5d"
dependencies: [4, 5, 6]
---

# Phase 7: Outbox operations, verified images, and release hardening

## Overview

Close the milestone with a transactional outbox contract, safe operational drain primitives, optional local PostgreSQL/backend Compose profiles, verified Docker Hub publication for first-party Python/backend images, CI integration, documentation, security checks, and the full cross-system acceptance gate. Kafka, a Python outbox consumer, and the web image remain later work; this phase only makes the backend handoff and current image supply chain reliable and explicit.

## Requirements

- Functional: every accepted domain command that changes an integration-relevant aggregate writes its contract-declared nonempty event set in the same transaction, with at most one event per changed aggregate version; an internal application port can lease/retry/acknowledge events idempotently without adding an HTTP route or pretending a consumer already exists.
- Security: future drain jobs require an explicit integration DB role/adapter; backend runtime has no writable analytics-artifact path; secrets are environment/infrastructure inputs; audit and logs redact payload secrets/PII.
- Operations: existing pipeline/dashboard Compose flows remain independent; optional backend/PostgreSQL profile binds loopback-only; CI runs Java and Python gates separately; protected release jobs publish tested images by immutable version/SHA tags and verify the returned digest; release/rollback instructions are documented.

## Architecture

```text
domain transaction
  ├─ operational row
  └─ outbox_event (same PostgreSQL commit)
             ↓ lease/retry (no Kafka yet)
      versioned JSON export/consumer boundary
             ↓ future phase
      Python Bronze -> existing quality/Gold pipeline
```

Outbox payload uses stable Java UUIDs plus canonical business codes and a contract version. It is not a second artifact writer. If a future adapter reads Gold, it must use the existing manifest-before/after/checksum fence and read-only mount; this phase does not add such a reader.

## Related Code Files

- Modify: `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\farm\application\FarmService.java`
- Modify: `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\farm\application\FieldService.java`
- Modify: `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\farm\application\CropService.java`
- Modify: `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\farm\application\SeasonService.java`
- Modify: `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\operations\application\ActivityService.java`
- Modify: `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\operations\application\ActivityLogService.java`
- Modify: `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\operations\application\HarvestService.java`
- Modify: `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\operations\application\EmployeeService.java`
- Modify: `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\inventory\application\WarehouseService.java`
- Modify: `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\inventory\application\MaterialService.java`
- Modify: `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\inventory\application\SupplierService.java`
- Modify: `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\inventory\application\InventoryTransactionService.java`
- Modify: `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\inventory\application\InventoryReversalService.java`
- Modify: `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\cost\application\OperatingCostService.java`
- Create: `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\integration\domain\OutboxEvent.java`
- Create: `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\integration\application\OutboxWriter.java`
- Create: `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\integration\application\OutboxDrainService.java`
- Create: `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\integration\infrastructure\OutboxRepository.java`
- Create: `D:\AgriInsight\backend\src\main\resources\db\migration\V18__create_outbox_tables.sql`
- Create: `D:\AgriInsight\backend\src\main\resources\db\migration\V19__add_outbox_rls_and_indexes.sql`
- Create: `D:\AgriInsight\backend\src\main\resources\contracts\agriinsight-operational-events-v1.schema.json`
- Create: `D:\AgriInsight\backend\src\test\java\com\agriinsight\backend\integration\OutboxAtomicityTests.java`
- Create: `D:\AgriInsight\backend\src\test\java\com\agriinsight\backend\integration\OutboxLeaseTests.java`
- Create: `D:\AgriInsight\backend\src\test\java\com\agriinsight\backend\integration\OutboxIntegrationRoleTests.java`
- Create: `D:\AgriInsight\backend\src\test\java\com\agriinsight\backend\integration\ArtifactBoundaryTests.java`
- Modify: `D:\AgriInsight\backend\ops\postgres\bootstrap-roles.sql` (add separate `NOLOGIN` integration permission role; no login/credential)
- Create: `D:\AgriInsight\compose.backend.yaml` (optional overlay/profile; root Compose remains unchanged)
- Modify: `D:\AgriInsight\Dockerfile` (harden and label the shared Python pipeline/dashboard image without changing its commands)
- Create: `D:\AgriInsight\.dockerignore` (exclude VCS, caches, runtime data, plans/reports, secrets, and generated artifacts from build context)
- Modify: `D:\AgriInsight\backend\Dockerfile` (harden phase 1's multi-stage Java 21 image for release)
- Create: `D:\AgriInsight\backend\.dockerignore` (minimal backend build context)
- Modify: `D:\AgriInsight\.github\workflows\ci.yml` to add Java 21 integration/security job without weakening Python job
- Create: `D:\AgriInsight\.github\workflows\publish-images.yml` (protected Docker Hub release only)
- Modify: `D:\AgriInsight\README.md`, `D:\AgriInsight\docs\architecture.md`, `D:\AgriInsight\docs\mvp-acceptance.md`
- Create: `D:\AgriInsight\docs\backend-development.md`, `D:\AgriInsight\docs\backend-deployment.md`
- Create: `D:\AgriInsight\scripts\backup-backend-postgres.ps1`, `restore-backend-postgres.ps1`

## Implementation Steps (TDD: red → green → refactor)

1. **Red — atomicity:** write tests that each successful integration-relevant command has exactly its declared event cardinality and aggregate/version set, a domain rollback has none, and a retry with the same idempotency key cannot create a second domain row or duplicate any event ordinal. A command affecting multiple integration aggregates may emit multiple events, but the contract fixture must name them and preserve deterministic ordinals.
2. **Red — lease/retry/order:** test two workers cannot lease the same event; claim returns a random lease token plus incremented generation/owner/expiry; ack/fail succeeds only for the current unexpired token/generation; and a stale worker cannot acknowledge after expiry, release, or another worker's reclaim. For two committed versions of one aggregate, version N+1 cannot lease before N is published; a dead-lettered predecessor blocks later versions and surfaces an operations failure. Different aggregates may lease concurrently. Test bounded exponential backoff, max-attempt transition to terminal `DEAD_LETTER`, no automatic dead-letter retry, and idempotent acknowledgement of already-published events. Do not implement a broker.
3. **Red — boundary:** test that no Java code resolves a writable path under `artifacts/`, no backend Compose service mounts artifacts read-write, and existing pipeline/dashboard Compose config still validates.
4. **Green — migration/schema:** add `outbox_events` with UUID, tenant, command-record UUID, event ordinal, aggregate type/id/version, event type, schema version, occurred_at, JSONB payload, status, attempts/max attempts, available/leased-until/published/dead-letter timestamps, lease owner/token/generation, bounded redacted last error, audit metadata, unique `(tenant_id, command_id, event_ordinal)`, unique `(tenant_id, aggregate_type, aggregate_id, aggregate_version)`, and claim/order indexes. Extend the pre-Flyway role template with a separate `NOLOGIN` integration permission role; V19 grants/policies allow it only the claim/ack columns/operations required across tenants. Runtime is not a member and normal application users get no arbitrary event read.
5. **Green — writer:** add a typed `OutboxWriter` port and invoke it from command services inside the same transaction after optimistic aggregate version assignment. Serialize only allowlisted fields; include `event_id`, `tenant_id`, `command_id`, `event_ordinal`, `aggregate`, `aggregate_id`, `aggregate_version`, `business_code`, `event_type`, `schema_version`, `occurred_at`, and payload. Never include bearer tokens/passwords/private paths; timestamps are metadata, not the ordering key.
6. **Green — drain port:** implement an internal application service using `FOR UPDATE SKIP LOCKED`, bounded batch size, per-aggregate predecessor gating, lease expiry, token/generation fencing, bounded retry/backoff, conditional acknowledgement, and terminal dead letter. A claim may select only the lowest unpublished event known for an aggregate; a dead-lettered predecessor blocks successors. Ack/fail SQL includes event id + lease owner + token + generation + unexpired leased state; zero updated rows is a stale-lease result, never success. Add no controller, scheduler, or public route in this milestone. Tests call the port with the dedicated integration role; a future adapter/worker must supply a separate login credential/member role and real delivery behavior.
7. **Green — delivery:** add an optional Compose overlay with PostgreSQL healthcheck, a one-shot `backend-role-bootstrap` gate holding only the local operator/`CREATEROLE` secret, a one-shot `backend-migrate` service holding only migration credentials and depending on the role gate, and a runtime backend service holding only the restricted credential and depending on successful migration completion. The role gate always uses the current idempotent script, so an existing phase-6 database receives/verifies the integration role before V19 references it. Tenant/first-admin provisioning remains an explicit operator action, never automatic container startup. Bind host ports to `127.0.0.1`, use an ignored D-local `backend/.runtime/postgres` bind for database data, do not mount `artifacts/` writable, and keep root `compose.yaml` plus pipeline/dashboard commands unchanged. Image layers may still consume Docker Desktop storage, so require disk PASS and an explicit Docker-data-on-D check before any pull/build.
8. **Green — first-party images:** harden the existing root Python image and phase 1's multi-stage backend image. Both run as non-root, contain no source-control metadata, build caches, secrets, generated analytics artifacts, or database data, declare OCI source/revision/version labels, and expose a deterministic smoke command. Publish only `${DOCKERHUB_NAMESPACE}/agriinsight-python` and `${DOCKERHUB_NAMESPACE}/agriinsight-backend`; do not republish PostgreSQL. Ordinary pull requests build/test without login or push. A protected release environment logs in with `DOCKERHUB_USERNAME` plus a least-privilege `DOCKERHUB_TOKEN`; tests, a pre-publish image scan, tag/version consistency, and build-context policy must pass before push. It then publishes immutable semantic-version and Git-SHA tags with SBOM/provenance, scans and smoke-tests the exact returned registry digest, and accepts no release whose post-push evidence fails. Pin third-party actions by reviewed commit SHA, mask credentials, and disable automatic `latest`. The future frontend plan adds `agriinsight-web` through the same reusable contract.
9. **Green — backup/restore drill:** add disk-guarded PowerShell wrappers around `pg_dump --format=custom` and `pg_restore` that accept an explicit D-local target, refuse overwrite, keep credentials out of command arguments/logs, preserve ACLs, record PostgreSQL/schema/checksum metadata, and never clean user files. Restore order is clean cluster -> current idempotent role bootstrap -> `pg_restore --no-owner` with ACLs -> Flyway validate -> role/grant/RLS/runtime/count smoke tests. Record measured restore time. Test an absent integration role is created before V19/restore and that bypassing the role gate produces an actionable failure. A production release must declare approved RPO, RTO, retention, encrypted off-host storage, and restore owner; without them the backend remains local/staging only.
10. **Green — CI/security:** add Java 21 unit + Testcontainers integration job, dependency/vulnerability/SBOM check, OpenAPI generation/diff check, secret scan, backup/restore drill, image build-without-push gate, and full Python regression steps. Do not auto-fix dependencies in CI.
11. **Green — docs:** document OIDC environment contract, role/scope matrix, migration ownership, runtime/integration DB roles, tenant provisioning, outbox schema/versioning, local commands, disk guard, Docker Hub namespace/token/immutable-tag contract, backup/restore/forward-repair rollback, production RPO/RTO/retention gate, and the explicit non-goals.
12. **Refactor/review:** run CK red-team and whole-plan consistency sweep; resolve every critical/high finding before recommending cook. Commit each logical cluster separately. Git and Docker pushes occur only at their explicit protected workflow gates.

## Outbox contract

- Event, command, and aggregate IDs are UUIDs; `aggregate_version` is the optimistic committed version and ordering key. Business codes are stable integration keys and may be null only for aggregates without a code.
- `schema_version` is an integer/string controlled by a checked-in JSON Schema. Breaking payload changes require a new version and consumer regression tests.
- Delivery is at-least-once. Consumers deduplicate by `event_id`; producers prevent retry duplicates with `(tenant_id, command_id, event_ordinal)`. The drain never claims exactly-once semantics.
- Claiming preserves order per aggregate version while allowing different aggregates in parallel. Consumer fixtures track the last applied aggregate version, ignore duplicate/stale versions, and reject/park an unexpected forward gap; `occurred_at` never decides order.
- Status transitions are audited and fenced: `PENDING -> LEASED -> PUBLISHED`; failed current leases return to delayed `PENDING` while attempts remain, then enter terminal `DEAD_LETTER`. Only the current unexpired lease token/generation may ack/fail. Payloads are immutable, and no production adapter in this milestone automatically marks an event published.
- Tenant/RLS rules prevent a normal user from reading another tenant's event. A future integration reader is separately least-privileged and audited.
- `max_attempts`, lease duration, and batch size come from bounded operator configuration, never event payload/client input. Dead-letter inspection/requeue requires a later audited operations command; this milestone never retries terminal rows silently.
- A dead-lettered aggregate version blocks later versions for that aggregate and raises an operational failure; it never lets a newer state overtake the failed event.

## Focused validation

- `powershell -ExecutionPolicy Bypass -File scripts/check-workspace-disk.ps1`
- `backend\mvnw.cmd -Dmaven.repo.local=..\artifacts\_tmp\m2-repository verify`
- Testcontainers atomicity/lease/RLS tests with real PostgreSQL.
- Fresh and phase-6-upgrade role bootstrap -> Flyway V18-V19 after the V16-V17 cost migrations, plus backup -> clean-cluster role bootstrap -> ACL-preserving restore -> Flyway/RLS/grant/runtime smoke drill with checksum and measured restore time; no production claim without approved RPO/RTO/retention/off-host encryption.
- `docker compose -f compose.yaml config --quiet` and `docker compose -f compose.yaml -f compose.backend.yaml --profile backend config --quiet` (run/pull only when Docker daemon and disk-location gates pass).
- Release dry run builds both first-party images without credentials or push; protected release verifies version/SHA tags, OCI labels, SBOM/provenance, vulnerability policy, and pull-by-digest smoke behavior before Docker Hub publication is accepted.
- `python -m pytest`, `python -m compileall -q src dashboard tests`, Node syntax check, wheel smoke test.
- `ck plan validate plans/260719-0753-backend-auth-rbac/plan.md` plus whole-plan grep for stale paths/decisions.

## Success Criteria

- [ ] Domain commit and outbox commit are atomic; duplicate retries are idempotent.
- [ ] Lease/retry/ack is token+generation fenced, stale acknowledgements fail, max attempts dead-letter, and no path is publicly exposed.
- [ ] Event schema/version and the distinct non-inheritable integration-role grants/RLS policies are documented and tested.
- [ ] Java cannot write artifacts, SQLite, Gold, or manifest; Python ownership tests remain green.
- [ ] Optional Compose overlay is loopback-bound, stores DB data on D, and leaves root pipeline/dashboard startup unchanged.
- [ ] Python and backend first-party images build reproducibly, run non-root, contain no secrets/runtime artifacts, publish only from the protected release job with immutable version/SHA tags, and pass an exact-digest smoke test; no automatic `latest` tag exists.
- [ ] CI has Java 21 and existing Python/Node gates; no secrets or caches are committed.
- [ ] Backup/restore drill passes from a D-local explicit target; production deployment is blocked until RPO, RTO, retention, encrypted off-host storage, and ownership are approved.
- [ ] Security/review/validation reports have zero unresolved critical/high findings.
- [ ] User-facing docs explain configuration, local disk guard, rollback, and deferred stages.

## Risk Assessment

- Outbox payload drift: JSON Schema + contract version + fixture tests; never edit old payload semantics silently.
- Lease duplication/loss: row locks, owner/token/generation fencing, conditional ack, lease expiry/reclaim, dead-letter bounds, idempotent consumer contract, and crash-recovery tests.
- Accidental drain activation: no HTTP route or scheduler in this milestone; future adapters require an explicit integration role, bounded lease contract, and authorization tests.
- Compose/Docker storage pressure: optional profile, D-local caches where possible, guard before pull/build, no cleanup of user data.
- Docker Hub compromise or tag drift: least-privilege expiring CI token, protected environment, masked secrets, pinned actions, immutable version/SHA tags, SBOM/provenance, digest-based verification, and immediate token revocation/republish under a new version after an incident.
- False “complete” integration: label Python consumer/Kafka/Gold refresh as deferred until a later plan; this phase proves only the safe handoff boundary.

## Rollback

Turn off the optional backend/outbox drain and keep the Python MVP. If a migration is faulty, stop writes and prefer an audited forward repair; restore only from a checksum-verified backup whose clean-restore drill passed and account for the approved RPO. Never drop outbox/domain tables or edit applied migration checksums. Revert additive CI/docs/Compose changes independently.
