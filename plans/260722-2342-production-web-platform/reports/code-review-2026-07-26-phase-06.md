# Phase 6 Work Operations Production-Readiness Review

## Code Review Summary

### Scope

- Focus: current uncommitted Phase 6 work operations changes.
- Files: 46 tracked/untracked implementation, test, E2E, navigation, BFF, demo-bootstrap, and plan files.
- Current lines inspected: 6,481. Tracked delta: +618/-31; untracked work tree inspected as full files.
- Protocol: spec-compliance, critical/informational checklist, adversarial review.
- Scout findings: same-route client-state reuse, silently truncated log/history pages, deterministic demo-assignment retry conflict. Delegated scout was interrupted by lead; findings below were independently traced against callers and backend contracts.

### Overall Assessment

**Blocking issues exist: 2 High findings. No Critical trust-boundary bypass, tenant escape, credential exposure, destructive in-place correction, or unbounded request-body defect found.**

The POST boundary is materially sound: trusted-host, same-origin, CSRF, session, bounded JSON, UUID path, strict DTO, exact-operation, idempotency-key, and bounded upstream transport checks are present. Spring remains the authoritative permission/tenant/ownership boundary. Merge should remain blocked because client state can cross work targets during same-route navigation and the UI silently presents only the first 50 log/history rows as the immutable record.

### Critical Issues

None found.

### High Priority — Blocking

#### 1. Same-route RSC navigation can carry a draft and retry key to a different activity/log

- Evidence:
  - `web/src/features/work/components/work-activity-detail.tsx:158` renders `AppendWorkLogForm` without an activity identity key.
  - `web/src/features/work/components/immutable-work-log-history.tsx:155` renders `CorrectWorkLogForm` without a log identity key.
  - Both forms contain uncontrolled fields plus local state/ref state (`append-work-log-form.tsx:28-37`, `correct-work-log-form.tsx:25-36`).
  - `web/src/features/work/use-idempotent-work-mutation.ts:22-25` fingerprints only `JSON.stringify(payload)`, not the target path.
  - Activity/log changes are same-route `Link` transitions (`work-activity-queue.tsx:43-50`, `immutable-work-log-history.tsx:62-68`), where React can reconcile the same client component instance.
- Trigger: enter a draft or receive an ambiguous network failure on activity/log A, then select activity/log B without a full reload. The component can retain A's uncontrolled draft and mutation refs while receiving B's path props.
- Impact:
  - stale draft can be submitted to the wrong activity/log;
  - identical payload on B can reuse A's idempotency key, conflicting with the backend key/path fingerprint and returning `409`;
  - correction kind/time/feedback can visually belong to the previous log.
- Fix:
  - key append form by `activity.id` and correction form by `selectedLog.id`;
  - reset draft/mutation state when target identity changes;
  - include canonical target path plus payload in the client retry fingerprint;
  - add a browser/component test that types on A, navigates to B, and proves draft reset plus a new key.

#### 2. Immutable log/history UI silently truncates at 50 rows

- Evidence:
  - `web/src/features/work/work-generated-client-adapter.ts:195-218` always requests logs and history with `limit: 50, offset: 0`.
  - `web/src/features/work/load-work-view-model.ts:223-228` retains the upstream `hasMore` signal.
  - `web/src/features/work/components/immutable-work-log-history.tsx:58-86` renders the first-page logs without using `hasMore`.
  - The lineage panel labels `history.items.length` as the event count at `immutable-work-log-history.tsx:134` and likewise ignores history `hasMore`.
  - By contrast, the activity queue explicitly discloses truncation at `work-activity-queue.tsx:67-71`.
- Trigger: an activity with more than 50 logs or a lineage with more than 50 events.
- Impact: older server-recorded entries become inaccessible, the displayed event count is misleading, and the Phase 6 acceptance claim of immutable server-backed history is false for long-lived activities.
- Fix: expose bounded offset/cursor pagination for logs and history, or add an explicit load-more flow. Until complete pagination exists, display the truncation boundary and do not represent the first page as the complete immutable history.

### Medium Priority

#### 3. Demo bootstrap is not idempotent after a seeded assignment is revoked

- Evidence:
  - `src/agriinsight/demo_tenant_sample_sql.py:102-106` derives a stable assignment primary key.
  - `src/agriinsight/demo_tenant_sample_sql.py:107-123` inserts only when no active row exists, but has no `ON CONFLICT`.
  - `src/agriinsight/demo_tenant_bootstrap_sql.py:45` claims the seed is idempotent.
- Trigger: revoke one of the three deterministic field-worker assignments, then rerun demo bootstrap.
- Impact: the active-row `NOT EXISTS` predicate passes, but the revoked row still owns the deterministic primary key; the insert fails and rolls back the demo seed/E2E setup.
- Fix: use a tenant-guarded upsert that reactivates the deterministic assignment, or use a lifecycle-safe new identifier. Add a PostgreSQL retry test with a revoked prior row; string-count tests do not prove this behavior.

#### 4. New POST route wiring lacks negative trust-boundary tests

- Evidence:
  - `web/tests/contracts/work-mutations.contract.test.ts:4-7` tests wrappers and `readBoundedJson`, while mocking the upstream client; it does not invoke either route handler or `authorizeWorkMutation`.
  - `web/tests/e2e/work-operations-mobile.spec.ts:120-134` proves denied page read only, not denied mutation.
- Risk: a future route reorder/removal of trusted-host, origin, CSRF, session, idempotency, or permission propagation can pass current Phase 6 tests.
- Fix: route-level tests for anonymous `401`, invalid host/origin/CSRF, missing/invalid idempotency key, invalid content type/body, and upstream `403/404/409`; assert no upstream call on pre-authorization failures.

### Low Priority

None worth blocking or adding review noise.

### Edge Cases Found by Scout

- Validated defect: client draft/ref lifetime is broader than activity/log target lifetime.
- Validated defect: `hasMore` is preserved but discarded for logs/history.
- Validated defect: revoked deterministic demo assignment defeats claimed seed idempotency.
- Verified non-issues:
  - no N+1 child loading before selection; assignments/logs load in parallel;
  - path IDs are exact UUIDs and cannot alter the allowlisted upstream route;
  - append/correction allowlists are separated from GET operations;
  - no fabricated `PATCH` or `If-Match`;
  - response/error mapping does not relay upstream body, stack, bearer token, or tenant diagnostics;
  - child assignment/log payloads are checked against selected `activityId`;
  - backend route registry and `ActivityLogService.requireScope` recheck `ACTIVITY_LOG_APPEND`, tenant/activity scope, active assignment, employee, and correction ownership before persistence/idempotency replay.

### Spec Compliance

| Requirement | Status | Evidence |
|---|---|---|
| Frozen generated GET contracts; no backend route addition | PASS | Generated types plus exact GET allowlist/adapters; no backend diff |
| Mobile-first `/work` without offline queue/upload/approval fiction | PASS in code; E2E not rerun in this review | Work route/components and 375 px scenario |
| Append with stable idempotent retry | PARTIAL / BLOCKED | Same-payload retry works; target identity absent from client fingerprint |
| Append-only correction with idempotency | PASS at transport/domain boundary | Exact POST correction route; backend append-only service |
| No fabricated `If-Match` | PASS | Mutation headers are exact and tested |
| Complete immutable server-backed history | FAIL | First page only; `hasMore` ignored |
| No speculative query layer/backend addition | PASS | Phase-local adapter/BFF only |

Recommendation: keep Phase 6 `in_progress`. Steps 1-7 substantially exist; Step 8 and acceptance remain incomplete until both High findings are fixed and focused browser/contract verification is rerun.

### Checklist Coverage

| Check | Result |
|---|---|
| Concurrency / async ordering | Blocking cross-target client-state/idempotency lifetime found; in-flight same-form double submit is guarded |
| Error boundaries | Exceptions mapped to sanitized problem responses or explicit generic read states |
| API contracts | Exact operation/path/header/body contracts checked; history pagination contract misrepresented |
| Backwards compatibility | No exported backend route or DB schema change |
| Input validation | Content type, 64 KiB actual bytes, UUIDs, strict DTO, field bounds checked |
| Authentication / authorization | Host + origin + CSRF + session at BFF; permission + tenant + ownership rechecked by Spring |
| N+1 / efficiency | No per-card child requests; selected child reads parallel; all reads bounded |
| Data leaks | No raw upstream error, bearer token, PII diagnostic, or stack trace exposed |
| Plan fact-check | Paths, operations, headers, Spring permission/service behavior grep-verified |

### Adversarial Review

- Accepted: cross-target draft/key reuse (High), silent immutable-history truncation (High), revoked demo assignment retry conflict (Medium).
- Rejected: “BFF lacks authorization” as a bypass — Spring route registry and scoped service enforce permission/tenant/ownership.
- Rejected: “POST lacks CSRF/host validation” — both trusted-request and same-origin/CSRF checks execute before session-backed upstream work.

### Metrics

- Type coverage: not measured.
- Test coverage: not measured.
- Lint issues: not measured.
- Fresh gates: not run per lead request to finalize from static evidence; do not infer pass status from existing tests.

### Recommended Actions

1. Isolate client form/mutation state by activity/log identity and include target path in retry fingerprints.
2. Implement bounded pagination/load-more for logs and lineage; test `hasMore: true`.
3. Make deterministic demo assignment bootstrap safe after revocation.
4. Add route-level negative authorization/CSRF/host/idempotency tests.
5. Rerun focused Phase 6 contracts, typecheck/lint, and the installed-Chrome `@work` journey after fixes.

### Unresolved Questions

- Is a product-level maximum of 50 lineage events explicitly accepted? The phase plan does not define that limit; current UI presents it as complete history.
- Should demo bootstrap reactivate revoked deterministic assignments, or intentionally create a fresh assignment lifecycle row?
