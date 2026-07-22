---
phase: 7
title: "Backend Phase 7 acceptance — outbox, images, and recovery boundary"
date: 2026-07-22
status: in-progress
---

# Backend Phase 7 acceptance

This report records the release-boundary evidence collected for the backend,
container registries, and clean-cluster backup/restore drill. The phase remains
open until the protected production release workflow and recovery policy receive
their required external configuration and approvals.

## Evidence collected

| Gate | Result | Evidence |
|---|---|---|
| Workspace disk guard | PASS | C: 10.44 GB free; D: 25.06 GB free after registry verification. Thresholds remain C 10/8 GB and D 25/20 GB. |
| Outbox atomicity | PASS | `OutboxAtomicityIntegrationTest`; committed event persisted with the domain command and rollback persisted neither. |
| Outbox lease/retry | PASS | `OutboxLeaseIntegrationTest`; aggregate ordering, lease fencing, stale acknowledgement rejection, bounded retry, and terminal dead-letter. |
| Outbox application unit tests | PASS | `CommandExecutionServiceTest` 5 tests and `OutboxDrainServiceTest` 1 test. |
| Tenant/RLS regression | PASS | `TenantRlsIntegrationTest` 9 tests. |
| Guarded backend verification | PASS | `scripts/run-backend-tests.ps1 verify`: 255 suites, 622 tests, 0 failures, 0 errors, 0 skipped; Flyway V1–V19 and PostgreSQL 18 integration completed successfully. |
| Python regression | PASS | `python -m pytest`: 76 passed, 3 expected optional-PDF skips in 38.44s. |
| Python/package checks | PASS | `compileall`, Node syntax check, wheel build/smoke, and SHA-256 artifact check. |
| Hosted CI | PASS | GitHub Actions run [`29932250984`](https://github.com/JasonTM17/AgriInsight/actions/runs/29932250984) passed Java, Python, dependency/configuration/secret scan, Python image scan/smoke, and backend image scan/smoke for commit `8d8463f9fe576aa98498125ae3dc845d9b432d82`. |
| Compose contract | PASS | Root Compose and backend profile both pass `docker compose ... config --quiet`; backend data binds to ignored D-local storage and artifacts are not mounted writable. |
| Image boundary | PASS | Root Python and backend Java images build with pinned base digests, OCI labels, and non-root users. The backend uses Temurin 21.0.11 JRE on Ubuntu Noble; Trivy 0.70.0 reports zero HIGH/CRITICAL findings and deterministic runtime smoke passes as UID/GID 10001. |
| Docker Hub phase publication | PASS | Python digest `sha256:ee4090812a36c48f180ee74aaa16995c79eabfedb6821d9764319643d06ba2f6`; CVE-clean backend digest `sha256:2fb346c3b85f03022866e74ae321a8a952b224fc23e43cb0560a440730019a5d`; backend tags `0.1.0-phase7` and `sha-8d8463f` match. |
| GHCR phase publication | PASS | The same immutable Python and backend digests are present at `ghcr.io/jasontm17/agriinsight-python` and `ghcr.io/jasontm17/agriinsight-backend`; both backend tags match Docker Hub, and pull-by-digest revision/non-root smoke passes. |
| Backup/restore drill | PASS | D-local custom dump SHA-256 `934ddd9db020d5a2e4f6860ce977663ec5a28bd68d4dcd7a16cc88a4c9c4162c`; metadata reports Flyway `19`; clean target restore elapsed 11.045s, role/RLS gate PASS, runtime schema rows 20. |
| Documentation validation | PASS | `node .claude/scripts/validate-docs.cjs docs/` exits 0; 12 internal links and 10 configuration references verified. Existing validator warnings are false positives for code/class names. |

## Phase 7 implementation covered

- Flyway V18/V19 `outbox_events` schema, FORCE RLS, order/claim indexes, and
  least-privilege integration grants.
- Typed committed-command event contract and JSON Schema v1.
- Transactional outbox writer, fenced `FOR UPDATE SKIP LOCKED` drain, retry
  backoff, stale lease rejection, and dead-letter terminal state.
- Loopback-only backend Compose overlay with role bootstrap/migration/runtime
  separation and D-local PostgreSQL data.
- Pinned, non-root first-party Python/backend images plus pull-request CI and
  protected Docker Hub/GHCR publication workflow.
- Disk-guarded custom-format PostgreSQL backup/restore wrappers and recovery
  documentation.

## Remaining production gates

1. Run the protected tag-triggered release workflow after configuring
   `DOCKERHUB_USERNAME`/`DOCKERHUB_TOKEN` and reviewer protection. The manual
   phase tags above are non-production evidence only.
2. Approve production RPO/RTO, retention, encrypted off-host backup storage,
   and restore ownership. No `latest` tag or PostgreSQL republish is allowed.

## Unresolved questions

- Production owner must approve RPO, RTO, retention, encrypted off-host backup
  storage, and the restore operator before any production deployment claim.
- GitHub Actions still needs the protected release environment secrets and
  reviewers; local Docker credentials are intentionally not copied into GitHub.
