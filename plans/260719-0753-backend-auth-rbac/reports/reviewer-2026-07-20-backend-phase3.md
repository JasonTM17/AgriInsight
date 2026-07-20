# Backend Phase 3 Code Review

## Scope

- Baseline/head: `d14353a..99d1712`
- Reviewed: 162 changed files; 10,518 insertions and 347 deletions
- Focus: role separation, RLS, tenant transaction ordering, exact authorization, tenant administration, idempotency, audit durability, concurrency, query bounds, and data exposure

## Overall Assessment

Phase 3 is acceptable as the local tenant authorization and administration foundation. It is not a full product production release. Source contracts, integration behavior, and final mechanical evidence agree on this boundary.

## Critical Issues

None remaining.

## High Priority

None remaining.

## Remediated Findings

1. Denial audit initially opened a `REQUIRES_NEW` transaction while the failed tenant business transaction still held the only Hikari connection. The one-connection-pool integration test produced `CannotCreateTransactionException` after waiting for a second connection. The final design carries redacted denial metadata in a typed exception, lets `TenantTransactionAspect` roll back and release the business connection, then records the denial in an independent transaction. Increasing the pool size would only hide the deadlock and was rejected.
2. A service-layer authorization denial outside the expected route path could reach the generic exception advice and become HTTP 500. The API advice now maps the typed denial to the same generic 403 contract, with regression coverage.
3. An integration assertion counted all audit rows and became order-dependent after another denial test ran first (`expected 6 but was 7`). The assertion now filters the lifecycle actions it owns, preserving isolation without weakening the behavior check.

## Informational Findings

- Denial audit persistence failure is logged by error type while the client still receives generic 403. This preserves authorization availability but can leave an audit gap; Phase 7 operations must alert on recorder failures and define retention.
- Farm, warehouse, and activity resolvers remain fail-closed extension points. Concrete FK-backed resolvers belong with their parent tables in Phases 4 and 5.
- Local verification is not registry provenance. Dependency/image scanning, SBOM/provenance, immutable Docker Hub tags, and pulled-digest smoke remain Phase 7.

## Production-Readiness Checklist

| Area | Result |
|---|---|
| Concurrency/state | Same-key idempotency, duplicate provisioning, optimistic version, last-admin locking, rollback/retry, and one-connection denial audit covered |
| Error boundaries | Typed auth denial and conflicts map to stable Problem Details; audit failure does not expose diagnostics or change 403 |
| API contracts | Exact ten method/template pairs across nine route families verified; DTO bounds/version headers match controllers and services |
| Compatibility | No Phase 1-2 contract silently removed; `/api/v1/me` remains available with enriched DB-backed authorities |
| Input validation | Path IDs, role codes, email/issuer/subject, idempotency key, versions, request fields, and query bounds validated at HTTP/application boundaries |
| Auth/authz | JWT trust checks precede exact bootstrap; route permission and service checks use DB state; runtime cannot self-grant or override tenant |
| Database isolation | Runtime is non-owner/non-superuser/non-`BYPASSRLS`; FORCE RLS, direct SQL, policy catalog, grants, and pooled reuse verified |
| Query efficiency | One bounded tenant-user list query and one joined principal-permission query; no N+1 in reviewed paths |
| Data leakage | No raw token, idempotency key, request body, response snapshot, credentials, SQL diagnostics, or stack trace exposed |
| Backward migration | Fresh V1-V4 and allowlisted V1-V3 adoption paths pass; unsafe role/owner/object states fail before mutation |

## Verification

- 54 Surefire suites: 134 tests PASS.
- 7 Failsafe suites: 23 PostgreSQL/Flyway tests PASS.
- Total: 61 suites, 157 tests, zero failures/errors/skips.
- Spring Modulith verification included.
- `git diff --check`, focused secret/risk scan, and disk guard PASS.
- No Testcontainers/Ryuk/PostgreSQL test container remained; unrelated Docker resources were untouched.

## Recommended Next Action

Start Phase 4 with tenant-bearing farm/season tables, composite tenant foreign keys, RLS policy catalog tests, and cross-tenant direct-SQL tests before implementing higher-level workforce/activity routes. Reuse the Phase 3 command/audit boundary; do not add a parallel idempotency mechanism.

## Unresolved Questions

- Production OIDC provider/token fixture is not selected.
- Tenant audit retention, alerting, and compliance ownership remain undecided.
- Docker Hub namespace/repository policy and protected release credentials remain undecided.
- Production backup RPO/RTO and restore ownership remain undecided.
