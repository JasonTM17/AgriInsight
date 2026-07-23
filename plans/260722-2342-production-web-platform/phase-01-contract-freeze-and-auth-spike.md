---
phase: 1
title: Contract freeze and auth spike
status: completed
priority: P1
effort: 5d
dependencies: []
---

# Phase 1: Contract freeze and auth spike

## Overview

Freeze the Spring contract before any production `web/` code exists. This phase first adds the bounded Work/Admin GET models that the eight product areas require, then ships one deterministic checked-in OpenAPI artifact plus a disposable auth spike that proves server-side session behavior under nonce, refresh, logout, and concurrent-tab pressure.

## Context links

- `plans/260722-2342-production-web-platform/plan.md`
- `docs/codebase-summary.md`
- `docs/project-overview-pdr.md`
- `plans/260719-0753-backend-auth-rbac/frontend-follow-up-brief.md`
- `plans/260719-0753-backend-auth-rbac/design-system/MASTER.md`

## Verified baseline

- Exact secured-route inventory already exists: `SecuredRouteRegistry` collects every contributor route, rejects duplicates, rejects wildcard business paths, and sorts exact `/api/v1/**` templates for stable comparison (`backend/src/main/java/com/agriinsight/backend/shared/api/SecuredRouteRegistry.java:23-79`).
- Route coverage is testable today: `IdentityEndpointInventoryTest` walks every controller mapping and fails if any app route is missing from the registry (`backend/src/test/java/com/agriinsight/backend/identity/IdentityEndpointInventoryTest.java:35-62`).
- The current authenticated bootstrap surface is small and explicit: `/api/v1/me` returns `profileId`, `tenantId`, `tenantCode`, `displayName`, `email`, `assurance`, `roles`, and `permissions` (`backend/src/main/java/com/agriinsight/backend/identity/api/CurrentUserController.java:11-19`, `backend/src/main/java/com/agriinsight/backend/identity/api/CurrentUserResponse.java:8-36`, `backend/src/main/java/com/agriinsight/backend/identity/api/CurrentUserRoutes.java:11-18`).
- Security is stateless today and API docs are only public in explicit dev/local mode (`backend/src/main/java/com/agriinsight/backend/shared/config/FoundationSecurityConfig.java:18-47`, `backend/src/main/java/com/agriinsight/backend/identity/infrastructure/IdentitySecurityConfig.java:85-133`, `backend/src/test/java/com/agriinsight/backend/ApiDocsExposureTest.java:27-34`, `backend/src/test/java/com/agriinsight/backend/DevelopmentApiDocsExposureTest.java:25-29`).
- Error and header contracts are already sanitized and correlation-aware: `ApiExceptionHandler` emits stable `ProblemDetail` bodies, `SecurityProblemWriter` writes `WWW-Authenticate` plus `X-Correlation-Id`, and `RequestCorrelation` constrains caller-supplied IDs (`backend/src/main/java/com/agriinsight/backend/shared/api/ApiExceptionHandler.java:36-174`, `backend/src/main/java/com/agriinsight/backend/shared/api/SecurityProblemWriter.java:41-141`, `backend/src/main/java/com/agriinsight/backend/shared/api/RequestCorrelation.java:8-30`, `backend/src/test/java/com/agriinsight/backend/shared/api/ApiExceptionHandlerTest.java:46-110`).
- Versioned reads and mutation headers are already real contracts, not plan fiction: reads emit `ETag`; mutations require `Idempotency-Key`, and versioned mutations/reversals parse strong `If-Match` values (`backend/src/main/java/com/agriinsight/backend/farm/api/FarmReadController.java:29-49`, `backend/src/main/java/com/agriinsight/backend/inventory/api/WarehouseReadController.java:29-49`, `backend/src/main/java/com/agriinsight/backend/shared/api/ApiCommandResponses.java:23-58`, `backend/src/main/java/com/agriinsight/backend/shared/api/IfMatchVersion.java:7-32`, `backend/src/main/java/com/agriinsight/backend/farm/api/FarmMutationController.java:49-117`, `backend/src/main/java/com/agriinsight/backend/inventory/api/InventoryTransactionMutationController.java:49-145`).

## Requirements

- Functional: add tenant/scope-safe GET models for activity assignments, activity logs/correction history, user roles, linked-identity status, farm/warehouse assignments, and a bounded tenant-audit timeline before freezing the route set.
- Functional: produce one deterministic Spring OpenAPI artifact for the resulting backend route set, including security scheme, shared error schema, and standard request/response headers needed by the web BFF.
- Functional: run the auth spike as a minimal pinned Next 16.2.11/React 19.2.8 application on Node 24, not a library-only harness, against Postgres-backed opaque sessions and a real containerized demo OIDC issuer; prove route-handler/proxy integration, nonce/state, refresh, logout, and concurrent-tab behavior.
- Functional: if Better Auth fails a must-pass case, switch the same spike harness to `openid-client` major 6 in this phase and rerun the identical matrix before Phase 3 begins.
- Security: browser storage must never contain access tokens, refresh tokens, or analytics credentials; all bearer handling stays server-side.
- Non-functional: the spike pins candidate versions (`better-auth` 1.6.24 and fallback `openid-client` 6.8.4) and commits its lockfile before tests; only the passing choice is later pinned in production `web/package.json`. No production `web/` runtime code starts here.

## Data flow

1. Contract flow: additive Work/Admin read models -> secured-route registry -> deterministic OpenAPI customizer -> canonical JSON artifact -> checked-in spec consumed by later codegen.
2. Auth spike flow: browser simulator -> OIDC auth code callback -> server session row in Postgres -> encrypted token columns -> opaque cookie only -> server-side refresh and logout paths.
3. Failure flow: malformed request or auth denial -> stable `ProblemDetail` + `X-Correlation-Id` -> no raw token, provider diagnostic, stack trace, or hidden route detail leaks.

## File matrix

- Modify only if a failing contract test proves it necessary: `backend/src/main/java/com/agriinsight/backend/shared/api/SecuredRouteRegistry.java`, shared error/security writers, or individual controllers. Prefer the global customizer over annotation churn.
- Create: `backend/src/main/java/com/agriinsight/backend/operations/api/ActivityAssignmentReadController.java` and bounded application/store queries for `GET /api/v1/activities/{id}/assignments`.
- Create: `backend/src/main/java/com/agriinsight/backend/operations/api/ActivityLogReadController.java` and bounded application/store queries for `GET /api/v1/activities/{id}/logs` plus `GET /api/v1/activities/{id}/logs/{logId}/history`.
- Create: resource-family read controllers/services for `GET /api/v1/users/{id}/roles`, `GET /api/v1/users/{id}/external-identities`, `GET /api/v1/farm-assignments`, `GET /api/v1/warehouse-assignments`, and bounded `GET /api/v1/audit-events`; exact filters, permissions, pagination, and non-enumerating 403/404 behavior are frozen by HTTP tests.
- Modify: every affected `SecuredRouteContributor` and endpoint-inventory test so each new exact GET template remains deny-by-default.
- Modify: `backend/src/test/java/com/agriinsight/backend/ApiDocsExposureTest.java`
- Modify: `backend/src/test/java/com/agriinsight/backend/DevelopmentApiDocsExposureTest.java`
- Modify: `backend/src/test/java/com/agriinsight/backend/shared/api/ApiExceptionHandlerTest.java`
- Create: `backend/src/main/java/com/agriinsight/backend/shared/config/OpenApiContractConfig.java` - canonical ordering, shared schemas, shared headers, and security scheme.
- Create: `backend/src/main/resources/contracts/agriinsight-api-v1.openapi.json` - checked-in deterministic backend contract for codegen.
- Create: `backend/src/test/java/com/agriinsight/backend/shared/api/OpenApiDeterminismTest.java`
- Create: `backend/src/test/java/com/agriinsight/backend/shared/api/OpenApiSecurityMetadataTest.java`
- Create: focused Work/Admin read HTTP contract tests covering tenant isolation, resource scope, pagination, redaction, and Supplier denial.
- Create: `scripts/export-backend-openapi.ps1`
- Create: `web-auth-spike/package.json`
- Create: `web-auth-spike/package-lock.json`
- Create: `web-auth-spike/README.md`
- Create: `web-auth-spike/src/provider-adapter.ts`
- Create: `web-auth-spike/src/postgres-session-store.ts`
- Create: `web-auth-spike/src/token-crypto.ts`
- Create: `web-auth-spike/src/oidc-flow.ts`
- Create: `web-auth-spike/src/app/api/auth/**` and `web-auth-spike/src/proxy.ts` - exercise the actual Next 16 integration boundary.
- Create: `web-auth-spike/tests/auth-flow.spec.ts`
- Create: `web-auth-spike/tests/concurrency.spec.ts`
- Create: `deploy/demo/keycloak/realm-template.json` and `scripts/configure-demo-oidc.ps1` - issuer/client/subject fixture without committed credentials; runtime secrets come only from protected/local environment inputs.
- Delete: none.

## Interface and contract freeze

- Work/Admin read contract is frozen before export: all collections are bounded and paginated; identity reads return safe provider/link status, never raw external subjects or claims; audit reads expose an allowlisted event envelope, never arbitrary metadata.
- Work GET authorization is separate from the existing append service: require `ACTIVITY_READ`; tenant-wide readers receive bounded tenant results, farm managers only assigned farm scope, and field workers only activities with an active assignment plus logs authored/assigned to them. History exposes the original and its append-only correction lineage but never mutates it. Reusing `ActivityLogService.requireScope()` is forbidden because that path requires `ACTIVITY_LOG_APPEND` and has command-specific ownership semantics.
- Backend artifact path is `backend/src/main/resources/contracts/agriinsight-api-v1.openapi.json`; regeneration must fail if the checked-in file drifts from the live route inventory.
- The artifact must derive paths from the secured-route registry, not a handwritten duplicate list, so exact method/template drift is caught mechanically.
- Shared metadata must include: `bearerAuth`, `ProblemDetail`-style error schema, `X-Correlation-Id`, `Idempotency-Key`, `If-Match`, and `ETag`.
- `/api/v1/me` is the canonical principal envelope for the web stack; Phase 3 may not invent parallel claims or a browser-side permission map.
- The auth spike must pass nonce/state replay rejection, refresh before expiry, local session revocation, revoked-session denial, and one-refresh-per-session-version under multi-tab concurrency. Provider token revocation/end-session is recorded as issuer capability: exercise it when advertised, but never weaken mandatory local logout because the issuer omits it.
- Better Auth is preferred only if it passes the whole matrix through Next 16 route handlers/proxy against Postgres-backed server sessions. Fallback target is `openid-client` 6.8.4 with the same cookie/session model and tests. The spike pins Next 16.2.11, React/React DOM 19.2.8, Better Auth 1.6.24, and a compatibility-tested TypeScript 5.x line; it never uses prerelease Auth.js v5 or unproven TypeScript 7.

## Tests before

- Extend route/spec tests so the route registry and the exported artifact must enumerate the same exact method/template set.
- Add failing HTTP tests for every missing Work/Admin read before implementing them: `ACTIVITY_READ` vs `ACTIVITY_LOG_APPEND`, field-worker active-assignment/author visibility, tenant-wide vs farm scope, bounded pagination, assignment/log lineage, safe linked-identity labels, audit redaction, Supplier denial, and route-registry coverage.
- Add deterministic export tests that generate the OpenAPI payload twice and compare canonicalized bytes, not just selected JSON paths.
- Add metadata tests that assert shared headers and sanitized 400/401/403/409 responses appear in the artifact.
- Build auth-spike tests first through real Next handlers: login callback, nonce replay, refresh near expiry, refresh-token rotation or replacement, logout, session revoke, concurrent-tab refresh race, proxy/host behavior, and server/client component token-leak checks.
- Fail the spike if any test needs browser token persistence, in-memory only state, or manual cookie rewriting to pass.

## Green steps

1. Add the missing Work/Admin GET controllers and dedicated read query services under existing resource families; reuse RLS/tenant context and exact-route contributors, but do not route reads through command-only `ActivityLogService.requireScope()`.
2. Prove every collection is bounded and every sensitive read is tenant/scope safe, redacted, and non-enumerating before it enters OpenAPI.
3. Add one shared OpenAPI config/customizer that sorts tags, paths, operations, schema keys, and shared header components before writing the canonical artifact.
4. Export the artifact through `scripts/export-backend-openapi.ps1`; fill controller summaries only where a failing metadata test proves the global config cannot infer the contract.
5. Create disposable minimal Next 16 `web-auth-spike/` outside the future `web/` tree with exact candidate pins and lockfile; start with Better Auth, Postgres-backed opaque sessions, encrypted token storage, and the credential-free demo Keycloak template configured at runtime.
6. Run the full auth race matrix. If Better Auth fails a must-pass case, switch the same harness to `openid-client` 6 and rerun without relaxing assertions.
7. Record the winner, issuer capabilities, loser failures, and cookie/session invariants. Phase 3 ports only the winner and deletes `web-auth-spike/` in the same commit group.

## Refactor

- Keep spec generation shared and global. Do not fork one OpenAPI rule set per module.
- Keep spike code isolated from production `web/` scaffolding so Phase 3 can port the chosen path and then delete the disposable package cleanly.
- Remove dead library adapters before phase close; the decision note carries the rejected path.

## Focused commands

- `backend\\mvnw.cmd -Dmaven.repo.local=..\\artifacts\\_tmp\\m2-repository -Dtest='*OpenApi*Test,*ApiDocsExposureTest,*DevelopmentApiDocsExposureTest,*ApiExceptionHandlerTest,*IdentityEndpointInventoryTest,*IdentitySecurityTests' test`
- `npm --prefix web-auth-spike test`
- `npm --prefix web-auth-spike run test:concurrency`

## Broad commands

- `powershell -ExecutionPolicy Bypass -File scripts/run-backend-tests.ps1 verify`
- `npm --prefix web-auth-spike test`
- `git diff --check`

## Acceptance

- [x] `backend/src/main/resources/contracts/agriinsight-api-v1.openapi.json` regenerates deterministically and is checked in.
- [x] Missing Work/Admin GETs are bounded, tenant/scope-safe, redacted, deny-by-default, and included before OpenAPI freezes.
- [x] The artifact documents the exact secured route set plus shared auth, error, and header metadata.
- [x] The chosen auth path passes nonce/state, refresh, local logout/revocation, and concurrent-tab tests with Postgres-backed opaque sessions; optional issuer revocation/end-session behavior is recorded accurately.
- [x] Browser storage holds no access token, refresh token, or analytics secret.
- [x] Better Auth is either accepted with evidence or rejected and replaced by `openid-client` 6 in the same phase.
- [x] The winner passes inside Next 16 route handlers/proxy against the real demo issuer; a Node-only library test cannot satisfy the gate.
- [x] Candidate spike dependencies and lockfile are exact before testing; production pins include only the passing choice after the matrix.

## Completion evidence

- Backend broad gate: 459 Surefire + 100 PostgreSQL/Testcontainers tests, zero failures/errors/skips.
- OpenAPI: 67 paths, 94 operations, 94/94 correlation request references, 13 versioned `ETag` references, deterministic SHA-256 `673b2dabb8853d75fff5b719fd1ecfaef350b0b076170e78a63b05fedbb7dfa8`.
- Auth: Better Auth 1.6.24 rejected by exact-package executable refresh race; `openid-client` 6.8.4 passed 16 unit, 7 PostgreSQL integration, production build, and 1 installed-Chrome/Keycloak E2E.
- Security review: all seven blocking findings resolved; final independent verdict `LAND`, 0 Critical and 0 High findings.
- Evidence reports: `reports/phase-01-contract-evidence-2026-07-23.md` and `reports/auth-spike-2026-07-23-oidc-session-verdict.md`.

## Risks and rollback

| Risk | L x I | Mitigation |
|---|---|---|
| New read endpoints leak tenant data or unbounded audit/log history | High x High | RED authorization/pagination/redaction tests, existing permission/RLS patterns, exact route registry |
| Spring docs stay nondeterministic | High x High | canonical serializer plus checked-in artifact test |
| Better Auth hides a refresh race | High x High | Postgres-backed concurrency tests and same-phase fallback |
| Shared header/error metadata drifts by controller | Medium x High | global OpenAPI components and spec assertions |
| Phase 3 starts before auth choice is proven | Medium x High | keep all auth work in `web-auth-spike/`; block Phase 3 until verdict exists |

Rollback: disable/revert the additive GET controllers, revert the OpenAPI customizer, and delete `web-auth-spike/`; Spring remains the current stateless resource server and no user-facing runtime changes ship.

## Dependencies, parallelization, and ownership

- Depends on: none.
- Blocks: Phase 2 and Phase 3.
- Parallelization: none. This phase owns the contract/auth decision boundary and must close first.
- File ownership: additive Work/Admin read models, backend contract/spec files, and `web-auth-spike/`. Later phases consume the frozen spec and must not recreate these reads or reopen the library choice without new failing evidence.

## Commit groups

1. `test(api): define work and admin read contracts`
2. `feat(api): add scoped work and admin reads`
3. `feat(api): export deterministic backend openapi artifact`
4. `test(auth): prove opaque-session auth race matrix`

## Validation log

- Tier: Standard
- Claims checked: 10
- Verified: 10
- Failed: 0
- Unverified: 0
