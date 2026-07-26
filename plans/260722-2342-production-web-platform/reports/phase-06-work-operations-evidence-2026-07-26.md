# Phase 6 Work Operations — Evidence Report (2026-07-26)

## Status: DONE

Phase 6 delivered mobile-first work assignment, append-log, append-only correction, and immutable history flows over the frozen Phase 1 `/api/v1/activities` contracts. No backend route or schema was added. Both High findings from `code-review-2026-07-26-phase-06.md` are fixed and verified.

## Review Finding Remediation

| # | Finding (severity) | Fix | Evidence |
|---|---|---|---|
| 1 | Same-route RSC navigation could carry draft + retry key to a different activity/log (High) | Append form keyed by `activity.id`; correction form keyed by `selectedLog.id`; client retry fingerprint now includes canonical target path | `work-activity-detail.tsx:165` (`key={activityId}`), `work-log-lineage-panel.tsx:105` (`key={selectedLog.id}`), `use-idempotent-work-mutation.ts:22` (`JSON.stringify([path, payload])`); E2E `@work target navigation resets draft and retry identity` proves draft reset + new key + new target |
| 2 | Immutable log/history UI silently truncated at 50 rows (High) | Bounded server-backed pagination for logs and lineage; `hasMore` drives `WorkPageControls`; lineage label shows `Sự kiện offset+1–offset+n` instead of claiming totals | `work-page-controls.tsx`, `immutable-work-log-history.tsx:73-81`, `work-log-lineage-panel.tsx:88-101`, `work-route-state.ts` (50-step bounded `logOffset`/`historyOffset` with `WORK_MAX_OFFSET` cap); contract tests "requests exact bounded offsets", "accepts only bounded 50-row route offsets" |
| 3 | Deterministic demo assignment retry conflict after revocation (Medium) | Bootstrap upsert reactivates the deterministic assignment (`SET revoked_at = NULL WHERE existing.id = …`) before conditional insert | `src/agriinsight/demo_tenant_sample_sql.py:108-130`; `tests/test_demo_tenant_bootstrap.py` 6/6 PASS |

The `FIELD_WORKER` persona now has a deterministic employee link (`DEMO-FIELD-WORKER`) and up to 3 deterministic activity assignments, so browser mutation proof is real, not mocked.

## Validation Evidence — all fresh runs on 2026-07-26

### Focused

- Backend HTTP contracts: `mvnw -Dtest=ActivityReadHttpContractTest,ActivityLogHttpContractTest test` → **5/5 PASS**, BUILD SUCCESS (`_tmp/phase6-backend-contract-tests.log`).
- `npm --prefix web run test -- work` → **21/21 PASS** across `work-operations.contract` (7), `work-mutations.contract` (6), `work-route-security.contract` (8).

### Broad

- `npm --prefix web run test` → **117 passed, 9 intentionally skipped** (126).
- `npm --prefix web run typecheck` → clean.
- `npm --prefix web run lint` → 0 warnings (`--max-warnings=0`).
- `npm --prefix web run contracts:check` → no generated-client drift.

### Real-browser E2E gate (`scripts/run-web-e2e-tests.ps1`)

Full guarded run PASS (`_tmp/phase6-web-e2e.log`):

- `DISK_GUARD overall=WARN exit_code=0` before and after (C freed from 6.1 GB FAIL to ≥9 GB via safe temp/browser-cache cleanup only).
- Static gates: vitest 117 pass, lint, typecheck, Next production build, contract drift.
- `OIDC_DEMO_CONFIGURED issuer=exact pkce=S256 personas=7`.
- `DEMO_BOOTSTRAP status=PASS database=agriinsight_demo` with reconciliation `{"errorCount": 0, "status": "passed"}`.
- Runtime schema validation PASS; 9/9 PostgreSQL db tests.
- `PLAYWRIGHT_E2E=PASS` — **6/6 scenarios in 36.8s** on installed Chrome, including all three `@work` journeys:
  1. field worker safely retries append and adds a correction (375 px, idempotent retry, append-only correction, CSP nonce checks);
  2. target navigation resets draft and retry identity (aborted first request, distinct keys and targets asserted);
  3. supplier receives a generic denied scope.
- `WEB_PLATFORM_E2E=PASS issuer=keycloak identity=spring-/me session=postgres browser=chrome`; compose environment fully cleaned up after run.

## Acceptance Criteria Verdict

| Criterion | Verdict |
|---|---|
| Consumes frozen Phase 1 generated client GETs, no backend routes added | PASS — exact GET allowlist/adapters; backend diff empty |
| `/work` on 375 px without fake offline/priority/team/approval/upload | PASS — E2E viewport 375×812, no-horizontal-overflow assertions |
| Append uses `Idempotency-Key`; retry cannot duplicate | PASS — path+payload fingerprint, E2E retry proof |
| Correction is append-only POST with `Idempotency-Key` | PASS — exact corrections route; no patch route exists |
| No fabricated `If-Match` on append/correction | PASS — mutation headers exact-tested |
| History timeline server-backed and immutable with bounded pagination | PASS — `hasMore`-driven controls, bounded offsets, no total-claim label |
| No speculative query layer/backend additions | PASS — phase-local adapter/BFF only |

## Unresolved Questions

- Product-level maximum offset (`WORK_MAX_OFFSET`) caps deep paging; UI states "Đã đạt giới hạn máy chủ" when reached. Accepted as bounded-read policy; revisit only if operators need unbounded lineage export (belongs to controlled report export, not this UI).
