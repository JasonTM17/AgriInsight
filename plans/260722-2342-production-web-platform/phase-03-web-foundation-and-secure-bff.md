---
phase: 3
title: "Web foundation and secure BFF"
status: pending
priority: P1
effort: "4-5d"
dependencies: [1, 2]
---

# Phase 3: Web foundation and secure BFF

## Overview

Create the production `web/` runtime: Node 24, npm, Next 16 App Router, and a secure BFF that keeps bearer handling server-side. This phase owns a web-specific Postgres session schema/role, encrypted token storage, CSRF/origin/host checks, and generated clients over both frozen OpenAPI contracts.

## Context links

- `plans/260722-2342-production-web-platform/plan.md`
- `docs/codebase-summary.md`
- `docs/design-guidelines.md`
- `plans/260719-0753-backend-auth-rbac/frontend-follow-up-brief.md`
- `plans/260722-2342-production-web-platform/phase-01-contract-freeze-and-auth-spike.md`

## Verified baseline

- The repository has no existing JS/web workspace in the verified repo shape; current responsibilities stop at `src/agriinsight/`, `dashboard/`, `tests/`, `backend/`, `scripts/`, `plans/`, and `docs/` (`docs/codebase-summary.md:7-15`).
- The frontend brief already froze the intended runtime shape: one `web/` Next App Router app, generated contracts, server components by default, and no browser token storage (`plans/260719-0753-backend-auth-rbac/frontend-follow-up-brief.md:14-19`, `84-90`, `104-109`).
- The design guidelines repeat the non-negotiables: versioned OpenAPI input, no browser token storage, no client-side KPI recomputation, and no hidden authorization in navigation (`docs/design-guidelines.md:3-4`, `13-24`).
- The backend still operates as a stateless resource server; browser BFF/session handling is explicitly deferred in the PDR, which is why this phase exists (`docs/project-overview-pdr.md:117-122`).
- Phase 1 is the only approved source of auth choice and backend contract artifact for this phase (`plans/260722-2342-production-web-platform/phase-01-contract-freeze-and-auth-spike.md`).

## Requirements

- Functional: create one `web/` Next 16 app on Node 24 and npm only; no monorepo split, no second JS app, no Turborepo.
- Functional: persist opaque sessions in a dedicated Postgres schema with separate web migrator and runtime roles, store encrypted provider tokens server-side, and expose only opaque cookies to the browser. Spring/Flyway never owns these tables; the web runtime has DML only and cannot run DDL.
- Functional: generate typed backend and analytics clients from the checked-in Phase 1 and Phase 2 artifacts; no hand-written adapter may masquerade as a generated contract.
- Security: enforce CSRF, origin/forwarded-host allowlists, secure cookies, security headers, and server-only bearer injection for every upstream call.
- Security: local session state is authentication/token-vault state only. Roles, permissions, tenant, and farm/warehouse authorization come from fresh `no-store` Spring reads per request, with request-local deduplication only.
- Non-functional: foundation only. Shared auth/session/BFF modules land here; route-area UI remains for later phases.

## Data flow

1. Browser -> `/auth/login` -> chosen OIDC adapter from Phase 1 -> callback -> session row in Postgres -> opaque `__Host-` cookie back to browser.
2. Browser GET -> Next server component or route handler -> server session lookup -> provider token decrypt -> fresh Spring `/api/v1/me` and required scoped resource reads -> exact generated client call -> normalized response to page code.
3. Browser mutation -> CSRF cookie/header + origin guard + session guard -> BFF route handler -> server-only bearer call upstream -> sanitized response.
4. Logout or revoke -> session row invalidated -> opaque cookie cleared -> further BFF requests fail closed and never leak stale tokens.

## File matrix

- Create: `web/package.json`
- Create: `web/package-lock.json` - only after the passing dependency and auth matrix from Phase 1 exists.
- Create: `web/tsconfig.json`
- Create: `web/next.config.ts`
- Create: `web/eslint.config.mjs`
- Create: `web/.env.example`
- Create: `web/src/app/layout.tsx`
- Create: `web/src/app/page.tsx`
- Create: `web/src/app/(auth)/login/page.tsx`
- Create: `web/src/app/api/auth/callback/route.ts`
- Create: `web/src/app/api/auth/logout/route.ts`
- Create: `web/src/proxy.ts`
- Create: `web/src/server/auth/provider.ts`
- Create: `web/src/server/auth/session-store.ts`
- Create: `web/src/server/auth/token-crypto.ts`
- Create: `web/src/server/auth/csrf.ts`
- Create: `web/src/server/auth/origin-guard.ts`
- Create: `web/src/server/auth/authorization-context.ts`
- Create: `web/src/server/bff/allowed-operation.ts` - internal exact method/template allowlist; never a caller-controlled upstream URL/path.
- Create: `web/src/server/clients/backend.ts`
- Create: `web/src/server/clients/analytics.ts`
- Create: `web/src/server/generated/backend/`
- Create: `web/src/server/generated/analytics/`
- Create: `web/db/migrations/001-create-auth-session-schema.sql`
- Create: `web/db/README.md` - dedicated schema/role, migration and rollback contract.
- Create: `web/db/bootstrap-roles.sql` and `web/db/configure-local-role-passwords.sql` - idempotent role bootstrap mirroring the backend's owner/migrator/runtime separation without embedding passwords.
- Create: `web/tests/auth/session-store.test.ts`
- Create: `web/tests/auth/oidc-flow.test.ts`
- Create: `web/tests/bff/csrf-origin.test.ts`
- Create: `web/tests/bff/token-leak.test.ts`
- Create: `web/tests/generated/backend-client.test.ts`
- Create: `web/tests/generated/analytics-client.test.ts`
- Delete: `web-auth-spike/` after the winning adapter/tests are ported and reproduced in `web/`.

## Interface and security contracts

- Session persistence: one opaque session ID maps to web-schema rows that hold encrypted upstream tokens, provider account linkage, expiry metadata, version/refresh fencing, and revoke timestamps. Tenant/role/resource scope stored locally is never authorization input. Enable account-token encryption and key rotation. A one-shot `agriinsight_web_migrator` owns DDL; `agriinsight_web_runtime` receives only required schema/table/sequence DML grants.
- Cookie policy: the `__Host-` session cookie is `HttpOnly`, `Secure`, `SameSite=Lax`, path `/`, and host-only. The double-submit CSRF cookie is intentionally readable by same-origin JavaScript, `Secure`, `SameSite=Lax`, path `/`; its header match, session bind, and origin check are required before mutations.
- Generated client input: both checked-in OpenAPI artifacts are mandatory and drift-tested before Phase 4.
- BFF contract: all upstream calls run on the server and must select an exact generated operation/method/template from an internal allowlist. There is no arbitrary catch-all proxy, caller-controlled base URL, or free-form upstream path.
- Authorization contract: each protected request derives current identity from Spring `/api/v1/me` and only the scoped catalogs needed for that operation with `cache: no-store`; navigation may use it for presentation, but upstream remains authoritative.
- CSRF/origin contract: GET/HEAD remain read-only; state-changing routes require session, origin match, and CSRF token match before any upstream network call.
- Proxy contract: trust `Forwarded`/`X-Forwarded-*` only from configured proxies and reject effective hosts outside the deployment allowlist. Emit CSP, frame, content-type, referrer, and permissions policies from one tested configuration.

## Tests before

- Write session-store tests before implementation: create, rotate, revoke, and concurrent refresh version fencing.
- Write DB privilege tests proving the migrator can apply/rollback the web chain while runtime can perform required session DML but cannot create/alter/drop tables, change grants, or access Spring business tables.
- Write auth-flow tests for callback success, state/nonce replay rejection, expired session, and logout.
- Write BFF tests that reject missing/invalid CSRF, foreign origin, and stale session before attempting any upstream fetch.
- Write allowlist/SSRF tests proving arbitrary path, method, host, scheme, encoded traversal, and redirect targets cannot reach either upstream.
- Write authorization tests proving stale local role/session fields never grant access when `/me` or scoped resource reads deny it.
- Write token-leak tests that prove no access token or refresh token appears in rendered HTML, JSON payloads, or logs captured by test harness.
- Write generated-client compile tests against the checked-in backend artifact before page code exists.

## Green steps

1. Scaffold one `web/` npm workspace with Node 24 engines, Next 16 App Router, and minimal server-only boot code.
2. Implement the chosen Phase 1 auth adapter behind `src/server/auth/provider.ts`; no route file should depend directly on library-specific primitives.
3. Add web-owned role bootstrap plus one-shot migration command, then encrypted token persistence with refresh/version fencing. Runtime startup validates schema version but never auto-migrates and never touches `backend/src/main/resources/db/migration`.
4. Implement auth routes, `src/proxy.ts`, security headers, trusted proxy/host rules, and internal BFF helpers. Later domain phases add explicit handlers; do not create a public catch-all proxy.
5. Generate backend and analytics clients from the two checked-in OpenAPI artifacts and fail CI on drift.
6. Port the winning Phase 1 adapter and race tests, reproduce the result in `web/`, then delete `web-auth-spike/` so only one JS application remains.

## Refactor

- Keep route handlers thin. Auth, session, crypto, and upstream client code stay in server modules with isolated tests.
- Keep shared auth/session files stable after this phase so later area phases only add domain loaders and route trees.
- Avoid client components until interaction actually requires them.

## Focused commands

- `npm --prefix web run typecheck`
- `npm --prefix web run test -- auth bff generated`
- `npm --prefix web run db:migrate:test`
- `npm --prefix web run db:privileges:test`

## Broad commands

- `npm --prefix web run lint`
- `npm --prefix web run test`
- `npm --prefix web run build`
- `powershell -ExecutionPolicy Bypass -File scripts/run-backend-tests.ps1 verify`

## Acceptance

- [ ] `web/` builds on Node 24 and npm with one Next 16 app.
- [ ] Browser-visible state contains only opaque cookies; no access or refresh token leaks.
- [ ] CSRF and origin checks fail before any upstream mutation call.
- [ ] Postgres session rows support revoke and refresh fencing.
- [ ] Session tables live in a dedicated web-owned schema/role; no Spring Flyway migration owns BFF state.
- [ ] Separate migrator/runtime role tests prove web runtime has no DDL, grant-management, or Spring business-table access.
- [ ] Backend and analytics clients are generated from the checked-in Phase 1/2 artifacts and used for exact allowlisted calls.
- [ ] Spring `/me` and scoped resource reads, not local session fields, authorize every protected operation.
- [ ] No arbitrary catch-all proxy or caller-controlled upstream URL/path exists.
- [ ] `web-auth-spike/` is removed after its winning behavior is reproduced in `web/`.

## Risks and rollback

| Risk | L x I | Mitigation |
|---|---|---|
| Session schema races on refresh | High x High | row-version fencing and concurrent refresh tests |
| Web runtime owns its schema and can alter session/audit guarantees | Medium x High | separate one-shot migrator and DML-only runtime roles with privilege tests |
| Tokens leak into client output | Medium x High | token-leak tests on HTML, JSON, and logs |
| Catch-all BFF becomes SSRF or authorization bypass | Medium x High | exact generated-operation allowlist and hostile path/host tests |
| Local session roles drift from Spring | Medium x High | fresh no-store `/me`/scope derivation; local state never authorizes |
| Shared auth files get churned by later UI phases | Medium x Medium | freeze ownership here; later phases own route trees only |

Rollback: disable `web/` startup and remove session cookies; Streamlit remains the current operator UI and backend resource-server behavior remains unchanged.

## Dependencies, parallelization, and ownership

- Depends on: Phases 1 and 2.
- Parallelization: none across the auth/schema/client boundary; both checked-in specs must exist first.
- Blocks: Phase 4 and every later route-area phase.
- File ownership: this phase owns `web/**` foundation, web-owned DB migrations, and generated-client core. It never edits Spring migrations. Later phases add exact domain route handlers/adapters without reopening auth/session/crypto.

## Commit groups

1. `feat(web): scaffold next16 runtime and owned schema`
2. `feat(auth): add opaque session store and oidc routes`
3. `feat(web): add generated clients and exact bff allowlist`
4. `test(web): lock csrf ssrf authorization and token gates`

## Success Criteria

- [ ] The BFF foundation exists and is secure by default.
- [ ] Session, CSRF, and server-only bearer boundaries are all mechanically tested.
- [ ] Shared web foundation files are ready for the shell and later route phases without reopening auth design.

## Validation log

- Tier: Standard
- Claims checked: 8
- Verified: 8
- Failed: 0
- Unverified: 0
