# Phase 6 Work Operations Remediation Review

## Code Review Summary

### Scope

- Focus: final remediation review of `fd8c905..8bc394e` against
  `code-review-2026-07-26-phase-06.md`, plus the current formatting-only
  `web/src/app/(platform)/work/page.tsx` diff.
- Range: 58 files, +5,191/-53. Documentation and prior reports were checked for
  claim accuracy; production findings focus on the executable Phase 6 surface.
- Primary files: Work route/page state, generated-client adapters, mutation
  handlers/security, pagination components, mutation hook, demo assignment SQL,
  focused contracts, and Work E2E.
- Protocol: scout first, spec compliance, critical/informational checklists,
  then adversarial review.
- Validation commands were not rerun per controller constraint. Controller
  evidence used: typecheck pass; focused/full ESLint pass; contracts check
  pass; Vitest 117 pass/9 skipped; focused Work 21 pass; Python demo 13 pass;
  Spring contracts 9 pass; npm audit 0; production build pass; Playwright 6/6.
- Scout result: both prior High runtime defects are fixed. One prior Medium
  finding is still a real database-lifecycle failure; one prior Medium test
  finding is only partially remediated.

### Overall Assessment

**No remaining Critical or High finding was found in the Phase 6 runtime Work
Operations surface. Do not mark the remediation complete or release Phase 6
yet: one prior Medium defect remains functional, and the prior route-negative
coverage finding remains partial.**

Cross-target form state and retry identity are now isolated. Logs and correction
lineage can page through the complete frozen backend offset range, with the
10,000 server cap disclosed rather than represented as a total. The request
stream is bounded by actual bytes, upstream errors are sanitized, and the
installed-Chrome flow exercises real append/replay/correction behavior.

The demo-assignment remediation is invalid against the existing database
trigger. A revoked assignment cannot be reactivated. The fresh-database E2E and
string-based Python assertion do not exercise that transition.

### Prior Finding Disposition

| Prior finding | Verdict | Evidence |
|---|---|---|
| High: draft/idempotency identity can cross activity/log targets | **RESOLVED** | `work-activity-detail.tsx:162-166` keys append state by activity; `work-log-lineage-panel.tsx:103-107` keys correction state by log; `use-idempotent-work-mutation.ts:20-25` fingerprints `[path, payload]`; Work E2E lines 120-167 proves draft reset and distinct target keys. |
| High: logs and correction history silently stop after 50 rows | **RESOLVED within the frozen contract** | `immutable-work-log-history.tsx:73-81` and `work-log-lineage-panel.tsx:88-101` drive page controls from `hasMore`; `work-generated-client-adapter.ts:110-138` forwards exact offsets; `work-route-state.ts:26-35` validates 50-row offsets; `work-page-controls.tsx:22-41` discloses the backend cap. |
| Medium: revoked deterministic demo assignment breaks idempotent bootstrap | **NOT RESOLVED** | `demo_tenant_sample_sql.py:107-113` sets a revoked row back to `NULL`, but `V5__create_farm_and_operations_tables.sql:331-332` rejects that transition as one-way through the trigger registered at lines 354-357. |
| Medium: POST routes lack negative trust-boundary tests | **PARTIALLY RESOLVED** | `work-route-security.contract.test.ts:66-167` now invokes both route handlers and covers foreign origin, expired session, missing idempotency, content type, invalid correction UUID, and sanitized `403/404/409`. It still does not route-test invalid Host, missing/invalid CSRF, malformed JSON, or an oversized streamed body. |

### Critical Issues

None found.

### High Priority

None found.

### Medium Priority

#### 1. Demo bootstrap still fails after the deterministic assignment is revoked

- Evidence:
  - `src/agriinsight/demo_tenant_sample_sql.py:107-113` executes
    `UPDATE activity_assignees ... SET revoked_at = NULL`.
  - `backend/src/main/resources/db/migration/V5__create_farm_and_operations_tables.sql:318-345`
    defines the assignment-history trigger. Lines 331-332 reject every update
    when `OLD.revoked_at IS NOT NULL` or `NEW.revoked_at IS NULL`.
  - The trigger is attached to `activity_assignees` at lines 354-357.
  - `tests/test_demo_tenant_bootstrap.py:105-111` only asserts that the
    prohibited SQL text exists; it does not execute the revoked lifecycle
    against PostgreSQL.
- Trigger: revoke a seeded assignment, then rerun the demo bootstrap.
- Impact: the update raises `Assignment revocation is one-way`; the transaction
  aborts before the conditional insert. The completion and evidence reports'
  claim that revoked-assignment reconciliation is fixed is false.
- Fix: never un-revoke the historical row. If bootstrap is intended to
  reassign the worker, insert a new lifecycle row with a new identifier only
  when no active logical assignment exists. Add a PostgreSQL test that seeds,
  revokes, reruns bootstrap, and verifies one active row plus the preserved
  revoked row.

#### 2. Route-level negative security coverage remains incomplete

- Evidence:
  - `web/tests/contracts/work-route-security.contract.test.ts:66-167` exercises
    handlers, but its request helper always supplies a valid Host, CSRF cookie,
    and CSRF header at lines 179-188.
  - The 64 KiB and streamed-body checks at
    `web/tests/contracts/work-mutations.contract.test.ts:123-164` invoke
    `readBoundedJson` directly, not either route handler.
  - Generic Host and CSRF helpers have separate BFF tests, but those do not
    prove both Work handlers retain and order the complete authorization
    boundary.
- Risk: a route-specific bypass or reorder that preserves the foreign-origin
  check but drops Host or CSRF enforcement can pass the focused Work route
  suite.
- Fix: invoke both handlers with invalid Host, missing/mismatched CSRF,
  malformed JSON, and oversized streamed bodies; assert the expected problem
  status and zero upstream calls.

### Low Priority

None worth adding.

### Edge Cases Found by Scout

- Pagination cap: verified non-issue. `WORK_MAX_OFFSET = 10_000` mirrors
  `ActivityLogReadController`'s `@Max(10_000)` frozen contract. Offset 10,000
  remains loadable; if that page reports `hasMore`, the UI says the server
  limit was reached instead of claiming complete totals.
- Request body: verified non-issue. `work-api-security.ts:100-137` reads the
  stream incrementally, counts actual bytes, cancels above 64 KiB, retains at
  most the bounded chunks, and decodes UTF-8 strictly. It does not trust
  `Content-Length` alone.
- Reload behavior: no blocking finding. `use-idempotent-work-mutation.ts:36-57`
  reloads only after a parsed successful response; ambiguous network failure
  keeps the same path-plus-payload key. The real E2E commits the first append,
  drops its response, retries with the same key, and observes one record before
  correction.
- Authorization and sanitization: no bypass found. Trusted Host, same-origin,
  CSRF, session, idempotency, strict path/body schemas, exact mutation
  allowlists, and Spring permission/scope checks remain layered. Upstream
  `403/404/409` bodies are not relayed.
- E2E count: the 6/6 gate comprises three Phase 6 Work journeys plus three
  platform/security journeys. The Work journeys cover real idempotent replay,
  append-only correction, cross-target reset, generic denied read, CSP, and
  375 px overflow.

### Spec Compliance

| Requirement | Status | Evidence |
|---|---|---|
| Frozen generated GETs; no backend route addition | PASS | Exact activity/assignment/log/history adapters and allowlists; no Phase 6 Spring route addition. |
| Mobile `/work` without fictitious offline/upload/approval behavior | PASS | Work UI plus provided installed-Chrome evidence. |
| Append with stable idempotent retry | PASS | Target path included in fingerprint; real ambiguous-response replay produces one record. |
| Append-only correction with idempotency and no `If-Match` | PASS | Exact correction POST and fixed mutation headers. |
| Server-backed immutable history with bounded pagination | PASS | `hasMore`-driven log/lineage controls through the frozen 10,000-offset boundary. |
| Deterministic local-demo remediation remains rerunnable after revocation | FAIL | Generated SQL violates the one-way assignment trigger. |
| Negative route trust-boundary regression coverage | PARTIAL | Core handler cases added; Host/CSRF/body boundary cases remain helper-only. |

Phase file status recommendation: reopen Phase 6 from `completed` until the
revoked-assignment lifecycle is fixed and executed against PostgreSQL. Plan
files were not changed by this review.

### Checklist Coverage

| Check | Result |
|---|---|
| Concurrency / async ordering | Cross-target mutation state fixed; in-flight duplicate submit guarded. Offset pagination follows the frozen contract. |
| Error boundaries | Route exceptions are converted to sanitized problem responses; no raw upstream body or stack returned. |
| API contracts | Exact GET/POST operation, path, query, header, and body boundaries verified. |
| Backwards compatibility | No backend route/schema change in Phase 6; the demo SQL conflicts with an existing immutable lifecycle invariant. |
| Input validation | UUIDs, strict DTOs, field bounds, content type, actual byte size, and UTF-8 checked. |
| Authentication / authorization | Layered BFF and Spring enforcement present; route-specific negative test matrix incomplete. |
| N+1 / efficiency | No per-card child reads before selection; selected assignments/logs load concurrently; pages are bounded. |
| Data leaks | No bearer token, tenant diagnostic, upstream body, or stack trace exposed in reviewed responses. |
| Plan fact-check | Prior findings, current paths/symbols, 10,000 backend cap, and one-way assignment trigger grep-verified. |

### Adversarial Review

- Accepted: revoked deterministic assignment "reactivation" contradicts the
  immutable database trigger (Medium).
- Accepted: Work handler negative matrix remains incomplete (Medium test gap).
- Rejected: 10,000 offset cap is a new silent truncation. The cap is the frozen
  backend contract and the UI discloses it when `hasMore` remains true.
- Rejected: the 64 KiB guard can be bypassed with chunked transfer. Actual
  streamed bytes are counted and the reader is cancelled at the boundary.
- Rejected: reload creates an automatic duplicate. Reload occurs only after a
  successful response; ambiguous failure retains the same target-bound key,
  and the real browser replay observed one record.
- Rejected: BFF-only permission checking allows mutation bypass. Spring remains
  authoritative for permission, tenant/activity scope, assignment, employee,
  ownership, and idempotency.

### Recommended Actions

1. Replace assignment un-revocation with a lifecycle-safe new row, or explicitly
   decide that bootstrap must respect revocation and leave the worker unassigned.
2. Add a real PostgreSQL seed -> revoke -> reseed test; string inspection is not
   behavioral proof.
3. Complete route-level invalid Host/CSRF/malformed/oversized-body tests for
   both mutation handlers.
4. Rerun the focused Python/PostgreSQL bootstrap test, 21 Work tests, and the
   installed-Chrome gate; then have the lead update phase/evidence status.

### Metrics

- Type coverage: not measured.
- Test coverage: not measured.
- Lint issues: 0 in supplied controller evidence.
- Supplied gates: typecheck pass; contracts pass; Vitest 117 pass/9 skipped;
  Work 21 pass; Python demo 13 pass; Spring contracts 9 pass; audit 0;
  production build pass; Playwright 6/6.

### Release Recommendation

**NO-GO for final Phase 6 acceptance/landing.** There is no runtime
Critical/High blocker in Work Operations, but a specifically reviewed
idempotency defect is still present and the completion evidence incorrectly
claims it is fixed. Release after the database lifecycle case is corrected and
proven on PostgreSQL. Finish the remaining route-negative matrix in the same
remediation cycle.

### Unresolved Questions

- After a demo assignment is revoked, should reseeding intentionally create a
  new assignment lifecycle, or should revocation remain authoritative and the
  demo worker stay unassigned?
