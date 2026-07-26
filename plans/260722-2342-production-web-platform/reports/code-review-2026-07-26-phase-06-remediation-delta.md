# Phase 6 Remediation Delta Review — 2026-07-26

## Scope

- Delta only: uncommitted diff (10 files, +137/-22) + new `scripts/test-demo-assignment-revocation.ps1`.
- Target: the two Medium findings from `code-review-remediation-2026-07-26-phase-06.md` (NO-GO).
- Read-only; no commands run beyond git/grep/read. Controller evidence: Python demo 13/13, vitest 127 pass/9 skip, focused work 31/31, typecheck+lint clean. Full E2E gate in flight.

## Verdict

**Both Medium findings RESOLVED. No side effects found. GO for the delta**, contingent only on the in-flight Playwright/probe gate finishing green (runtime evidence for criteria 3/5; static analysis below shows no reason it should fail).

## Finding 1 — revoked-assignment bootstrap lifecycle: RESOLVED

- Un-revoke UPDATE removed: seed now contains zero UPDATE against `activity_assignees` (`src/agriinsight/demo_tenant_sample_sql.py:106-136`); absence asserted at `tests/test_demo_tenant_bootstrap.py:115`.
- Trigger cannot fire from seed: `activity_assignees_revoke_only` is `BEFORE UPDATE` only (`V5__create_farm_and_operations_tables.sql:354-357`); `INSERT ... ON CONFLICT (id) DO NOTHING` performs no UPDATE on conflict — row skipped at the `id UUID PRIMARY KEY` index (V5 table def).
- Lifecycle matrix (all inside one transaction, `BEGIN` + `\set ON_ERROR_STOP on`, `demo_tenant_bootstrap_sql.py:59-60`; psql failure → `bootstrap-demo-environment.ps1:85-89` throws):
  - Fresh DB: `NOT EXISTS(active)` passes → 3 inserts.
  - Idempotent rerun: active rows exist → `NOT EXISTS` yields 0 rows → no conflict, no update.
  - Rerun after revocation: no active row for the triple → insert attempted with same deterministic id → PK conflict → DO NOTHING → revoked row untouched, worker stays unassigned (matches decided direction).
  - Deterministic id bound to different same-tenant activity/employee: guard DO-block raises `demo activity assignment id is bound elsewhere` → whole seed rolls back → bootstrap exits nonzero. Fail closed.
- Partial unique index `ux_activity_assignees_active` unreachable: `NOT EXISTS(active)` filters the only path that could violate it; seed is a single psql session, no concurrent writer.
- PostgreSQL behavioral proof: `scripts/test-demo-assignment-revocation.ps1` seed→revoke→reseed→verify:
  - Revocation UPDATE satisfies trigger contract exactly: `version + 1`, `updated_at = CURRENT_TIMESTAMP`, NULL→NOT NULL (ps1:57-60 vs V5:331-343).
  - Verification demands exactly `1|0|1` (preserved revoked row by id / zero active for the triple / one total history row) else throws (ps1:146-155) — proves no resurrection and no duplicate lifecycle row.
  - Fail-closed inputs: loopback host whitelist (ps1:14-16), DB pinned `agriinsight_demo` (17-19), PGPASSWORD required, never echoed (20-22), `ON_ERROR_STOP=1`, exit codes checked after both psql calls, exactly-one-revoked regex (87-89), GUID validation (95-97).
  - No reconciliation false positive: `demo_tenant_inspection_sql.py` inspects farms/fields/crops/seasons/warehouses/materials/personas only — no assignment counts — so the probe's inner bootstrap re-run passes with 2 active assignments.
- Runner wiring correct: `PGPASSWORD = $backendMigratorPassword` at `run-web-e2e-tests.ps1:448`; bootstrap 449-455; probe 456-462 (port 55443); operator-password swap only afterwards (476); Playwright at 584.
- Freshness per gate run: runner sets `AGRIINSIGHT_DEMO_POSTGRES_DATA_DIR=./artifacts/_tmp/web-e2e/postgres` (line 316) inside `$artifactRuntimeRoot`, wiped at start (380) and cleanup (627-631) → every gate run seeds 3, revokes 1, leaves 2 active. Standalone repeated probe runs against a persisted DB eventually hit "Expected exactly one active demo assignment to revoke" — loud failure, acceptable for test infra.

## Finding 2 — route-level negative security matrix: RESOLVED

- 10 new tests = `it.each(workRoutes)` x 5 cases over both real handlers imported from the route files (`work-route-security.contract.test.ts:6-7,51-64`) — route-level, not helper-level, closing the prior gap exactly (invalid Host, missing CSRF, mismatched CSRF, malformed JSON, oversized streamed body; both handlers).
- Status codes verified against implementation:
  - Invalid Host → 400: `assertTrustedRequest` throws `AuthError("invalid_host", 400)` on mismatched or missing host header (`environment.ts:158-164`); first check in `authorizeWorkMutation` (`work-api-security.ts:43`); tests assert `requireSession` not called.
  - Missing CSRF → 403: helper deletes header on empty override (test:282-285); `assertCsrf` throws 403 on absent header (`csrf.ts:16-25`); ordered before `requireSession` (`work-api-security.ts:46-52`); `requireSession` not called asserted.
  - Mismatched CSRF → 403: `headerValue !== cookieValue` (`csrf.ts:21`).
  - Malformed JSON → 400 with `code: "invalid_json"` asserted (`work-api-security.ts:89-97`).
  - Oversized body → 413 with `code: "request_too_large"` asserted; 65*1024 payload + JSON overhead > 65,536; bounded stream counting path `work-api-security.ts:100-122` (Content-Length not trusted alone).
- All 10 assert zero calls to mocked `executeAllowedMutation` (upstream). Both routes share `authorizeWorkMutation` + `readBoundedJson` (`logs/route.ts:21-23`, `corrections/route.ts:23-25`), so identical ordering is exercised per handler.
- Count consistency: focused Work suite went 21 → 31 (+10) per controller evidence.

## Side-effect sweep (criterion 6)

- `README.md`: gate paragraph text only.
- `backend/pom.xml`, `pyproject.toml`, `web/package.json`: description-string metadata only; versions unchanged. Inert.
- `web/src/app/(platform)/work/page.tsx`: pure indentation of 3 JSX props; identical semantics.
- E2E count: constant `ACTIVE_WORK_ITEMS_AFTER_REVOCATION_PROBE = 2` used at both assertions (spec:52-53,133-134); no `toHaveCount(3)` remains anywhere under `web/tests/`; only this spec references `work-activity-card`. Backend scopes the field-worker activity list by `activity_assignment.revoked_at IS NULL` (`ActivityScopeSql.java:60-63`), so seed 3 − probe 1 = 2 cards matches runner order bootstrap(449) → probe(456) → Playwright(584). Remaining journeys click `.first()` of 2 active cards — unaffected.
- No backend Java, migration, OpenAPI, or route change in diff. No public contract touched.

## Informational (non-blocking)

1. RLS dead branch in the guard: `activity_assignees` is FORCE RLS (`V6:19-20`) and the `migration_tenant_isolation` policy scopes migrator visibility to the current tenant (`V6:102-105`). Under `SET LOCAL app.tenant_id`, the guard's `existing.tenant_id <> tenant` branch can never observe a foreign-tenant row holding the deterministic id; that scenario degrades to silent DO NOTHING skip instead of RAISE. Threat model: demo DB is pinned single-tenant (`current_database() = 'agriinsight_demo'` + server-marker preamble guard), loopback-only; a cross-tenant squatter requires manual out-of-band writes. Same-tenant collisions — the realistic ones — fail closed. Harmless defense-in-depth; no action required.
2. Oversized-body test may hit the Content-Length precheck instead of the stream counter depending on whether NextRequest surfaces content-length for string bodies; both paths return 413 `request_too_large` at the route boundary, and the stream counter retains direct coverage in `work-mutations.contract.test.ts`. Prior finding demanded route-level coverage — satisfied.
3. Python assertions remain string-level, but are now paired with the real PostgreSQL probe — exactly the fix shape the prior review's Recommended Action 2 requested.

## Recommended actions

1. Land after the in-flight gate reports probe `DEMO_ASSIGNMENT_REVOCATION=PASS preserved=1 active=0 history=1` and Playwright 6/6 — that is the only evidence still pending.
2. Lead may close both Medium findings and lift the NO-GO for Phase 6 acceptance.

## Unresolved questions

- None blocking. The RLS-shadowed cross-tenant guard branch (Informational 1) can be left as-is or simplified later; behavior is safe either way.
