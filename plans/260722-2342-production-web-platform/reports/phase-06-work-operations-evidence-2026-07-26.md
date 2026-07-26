# Phase 6 Work Operations — Evidence Report (2026-07-26)

## Status: DONE

Phase 6 delivered mobile-first work assignment, append-log, append-only correction, and immutable history flows over the frozen Phase 1 `/api/v1/activities` contracts. No backend route or schema was added. Both High findings from `code-review-2026-07-26-phase-06.md` and both Medium findings from `code-review-remediation-2026-07-26-phase-06.md` (invalid demo un-revoke SQL; incomplete negative route matrix) are fixed and behaviorally verified.

## Review Finding Remediation

| # | Finding (severity) | Fix | Evidence |
|---|---|---|---|
| 1 | Same-route RSC navigation could carry draft + retry key to a different activity/log (High) | Append form keyed by `activity.id`; correction form keyed by `selectedLog.id`; client retry fingerprint now includes canonical target path | `work-activity-detail.tsx:165` (`key={activityId}`), `work-log-lineage-panel.tsx:105` (`key={selectedLog.id}`), `use-idempotent-work-mutation.ts:22` (`JSON.stringify([path, payload])`); E2E `@work target navigation resets draft and retry identity` proves draft reset + new key + new target |
| 2 | Immutable log/history UI silently truncated at 50 rows (High) | Bounded server-backed pagination for logs and lineage; `hasMore` drives `WorkPageControls`; lineage label shows `Sự kiện offset+1–offset+n` instead of claiming totals | `work-page-controls.tsx`, `immutable-work-log-history.tsx:73-81`, `work-log-lineage-panel.tsx:88-101`, `work-route-state.ts` (50-step bounded `logOffset`/`historyOffset` with `WORK_MAX_OFFSET` cap); contract tests "requests exact bounded offsets", "accepts only bounded 50-row route offsets" |
| 3 | Deterministic demo assignment retry conflict after revocation (Medium) | First remediation attempt (`SET revoked_at = NULL`) was invalid: it violated the one-way revocation trigger (`V5__create_farm_and_operations_tables.sql:331-357`). Final fix keeps revocation authoritative: the un-revoke UPDATE is removed, the conditional insert adds `ON CONFLICT (id) DO NOTHING`, and a fail-closed guard raises if the deterministic id is bound to a foreign tenant/activity/employee. A revoked demo assignment stays revoked after reseed; the worker remains unassigned for that activity | `src/agriinsight/demo_tenant_sample_sql.py:106-135`; `tests/test_demo_tenant_bootstrap.py` 13/13 PASS incl. no-un-revoke and guard assertions; PostgreSQL lifecycle probe `scripts/test-demo-assignment-revocation.ps1` wired into the E2E runner → `DEMO_ASSIGNMENT_REVOCATION=PASS preserved=1 active=0 history=1` |
| 4 | Route-level negative trust-boundary tests incomplete (Medium, from remediation review) | Both Work POST handlers now route-tested for invalid Host (400), missing and mismatched CSRF (403), malformed JSON (400 `invalid_json`), and oversized streamed body (413 `request_too_large`), each asserting zero session/upstream work | `web/tests/contracts/work-route-security.contract.test.ts:94-214` (10 new `it.each` cases across append + correction) |

The `FIELD_WORKER` persona now has a deterministic employee link (`DEMO-FIELD-WORKER`) and up to 3 deterministic activity assignments, so browser mutation proof is real, not mocked. During the E2E gate the lifecycle probe intentionally revokes one assignment before Playwright, so the `@work` journeys assert exactly 2 active cards — proving reseed respects revocation end to end.

## Validation Evidence — all fresh runs on 2026-07-26

### Focused

- Backend HTTP contracts: `mvnw -Dtest=ActivityReadHttpContractTest,ActivityLogHttpContractTest test` → **5/5 PASS**, BUILD SUCCESS (`_tmp/phase6-backend-contract-tests.log`).
- `python -m pytest tests/test_demo_tenant_bootstrap.py tests/test_demo_tenant_reconciliation.py` → **13/13 PASS** (revocation-safe SQL assertions included).
- `npm --prefix web run test -- work` → **31/31 PASS** across `work-operations.contract` (7), `work-mutations.contract` (6), `work-route-security.contract` (18, including the 10-case negative Host/CSRF/JSON/oversize matrix).

### Broad

- `npm --prefix web run test` → **127 passed, 9 intentionally skipped** (136).
- `npm --prefix web run typecheck` → clean.
- `npm --prefix web run lint` → 0 warnings (`--max-warnings=0`).
- `npm --prefix web run contracts:check` → no generated-client drift.
- `npm --prefix web audit --omit=dev --audit-level=high` → 0 vulnerabilities.

### Real-browser E2E gate (`scripts/run-web-e2e-tests.ps1`)

Static and runtime gates both passed after remediation. The full static
invocation established clean install, contract, test, lint, typecheck, and
production-build evidence before stopping at the newly added lifecycle probe.
After the probe's missing RLS tenant scope and the resulting two-card E2E
fixture expectation were corrected, the final runtime rerun used
`-SkipStaticGates` and produced auditable logs at
`_tmp/e2e-remediation-final2.stdout.log` and
`_tmp/e2e-remediation-final2.stderr.log`:

- `DISK_GUARD overall=WARN exit_code=0` before and after (C 9.25 GB, D 20.97 GB — above hard-fail thresholds).
- Static invocation: clean `npm ci` (441 packages), Vitest **127 pass / 9 skips**, zero-warning lint, typecheck, Next production build, contract drift clean.
- `OIDC_DEMO_CONFIGURED issuer=exact pkce=S256 personas=7 claims=aud+token_use credentials=environment-only`.
- `DEMO_BOOTSTRAP status=PASS database=agriinsight_demo` with reconciliation `{"errorCount": 0, "status": "passed"}`.
- **`DEMO_ASSIGNMENT_REVOCATION=PASS preserved=1 active=0 history=1`** — real PostgreSQL seed → revoke → reseed probe: the revoked row is preserved, reseed does not resurrect it, no duplicate lifecycle row is created.
- Runtime schema validation PASS; 9/9 PostgreSQL db tests.
- `PLAYWRIGHT_E2E=PASS` — **6/6 scenarios in 25.9s** on installed Chrome, including all three `@work` journeys against the post-revocation state (2 active assignments, one intentionally revoked):
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
| Demo reseed preserves one-way assignment revocation | PASS — real PostgreSQL lifecycle marker `preserved=1 active=0 history=1` |
| No speculative query layer/backend additions | PASS — phase-local adapter/BFF only |

## Unresolved Questions

- Product-level maximum offset (`WORK_MAX_OFFSET`) caps deep paging; UI states "Đã đạt giới hạn máy chủ" when reached. Accepted as bounded-read policy; revisit only if operators need unbounded lineage export (belongs to controlled report export, not this UI).
