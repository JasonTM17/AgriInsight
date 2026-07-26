# Code Review Summary

## Scope

- Range: `8bc394e..aa5609f` plus worktree reconciliation state.
- Files: 17 changed, 629 insertions, 54 deletions.
- Focus: final Phase 6 remediation landing review.
- Evidence inspected: Phase 6 plan/evidence and prior reviews; V5/V6/V9 assignment migrations; demo seed SQL and Python assertions; lifecycle probe and E2E runner; Work mutation handlers, security helper, route contracts, view-model/paging contracts, mobile E2E; final stdout/stderr logs.
- Worktree state before this report: clean. No additional uncommitted documentation reconciliation remained to review.
- Scout findings: one-way assignment lifecycle, forced-RLS visibility, mutation authorization/body-validation ordering, cross-target form identity, retry-key identity, bounded paging, and split static/runtime gate provenance.

## Overall Assessment

**Verdict: LAND.**

Blocking pass: **0 Critical, 0 High, 0 Medium**. Both prior Medium findings are closed, the earlier High fixes remain intact, and Phase 6 acceptance claims match the inspected code and supplied runtime evidence.

The evidence correctly describes two sequential gates. The first invocation supplied clean install/static evidence and stopped at the new lifecycle probe. The final runtime invocation explicitly used `-SkipStaticGates`; its log contains `STATIC_GATES=SKIPPED`, then the lifecycle marker, 9/9 database tests, 6/6 Playwright scenarios, `PLAYWRIGHT_E2E=PASS`, `WEB_PLATFORM_E2E=PASS`, and successful cleanup. It is not represented as one unified invocation.

## Critical Issues

None.

## High Priority

None.

## Medium Priority

None.

## Low Priority

### L1 — Foreign-tenant assignment-ID guard cannot observe rows hidden by forced RLS

`src/agriinsight/demo_tenant_sample_sql.py:126-135` checks whether the deterministic assignment ID is bound to another tenant/activity/employee. `activity_assignees` has forced RLS and the migrator policy only exposes the current tenant (`V6__add_farm_and_operations_rls_policies.sql:19-20,102-105`). A hypothetical global UUID collision owned by another tenant could therefore make `ON CONFLICT (id) DO NOTHING` skip the insert while the follow-up guard sees no row.

Impact is demo-only and low probability: the probe is loopback/exact-database guarded, IDs are deterministic UUIDs, and this cannot resurrect or expose revoked history. Follow-up: either narrow the evidence wording to a visible-tenant identity mismatch or add a reconciliation assertion that the expected assignment exists. Do not weaken RLS to implement the check.

## Edge Cases Found by Scout

- **Revoked deterministic assignment:** seed contains no assignment `UPDATE`; `ON CONFLICT (id) DO NOTHING` preserves the revoked row. V5 rejects any un-revoke (`V5__create_farm_and_operations_tables.sql:318-357`).
- **RLS lifecycle proof:** the probe uses `SET LOCAL app.tenant_id`, revokes exactly one active row, reseeds, and requires exact state `preserved=1 active=0 history=1`. Final runtime log records that marker.
- **Mutation trust boundaries:** both append and correction routes cover invalid Host, missing CSRF, mismatched CSRF, malformed JSON, and streamed oversize input. Host/CSRF failures assert no session or upstream call. Body failures occur after session authorization by design and assert no upstream call.
- **Error propagation/data exposure:** known auth, work, and validation errors map to bounded problem responses; unexpected failures become sanitized 502 responses. Upstream 403/404/409 bodies are not relayed.
- **Cross-target state:** append form is keyed by activity ID and correction form by selected log ID, so draft and hook state reset when the mutation target changes. The E2E proves distinct targets and idempotency keys after navigation.
- **Retry identity:** unchanged path+payload retries retain the idempotency key; successful mutation clears it. Contract and real-browser evidence cover both behavior branches.
- **Paging:** log and immutable-history requests preserve requested 50-row offsets, validate returned offset/limit, honor upstream `hasMore`, reject non-page-aligned/out-of-range offsets, and cap at 10,000.
- **Concurrency/query efficiency:** demo bootstrap is serialized by the runner mutex and the active-assignment partial unique index prevents duplicate active logical assignments. No new database-call loop or N+1 path was introduced in this remediation.

## Positive Observations

- The lifecycle invariant is proven against real PostgreSQL under tenant RLS rather than inferred from generated SQL.
- The route tests exercise the exported handlers, not only helper functions, and prove rejected requests do not reach the backend mutation client.

## Recommended Actions

1. Land Phase 6.
2. Track L1 as non-blocking evidence-hardening work.
3. Leave the overall plan `in_progress`; the Phase 6 checklist is complete and Phase 7 remains next.

## Metrics

- Type coverage: not instrumented; TypeScript typecheck passed.
- Test coverage: not instrumented.
- Focused Work tests: 31/31 passed (7 operations, 6 mutations, 18 route security).
- Broad web tests: 127 passed, 9 intentional skips.
- Demo Python tests: 13/13 passed.
- PostgreSQL privilege tests: 9/9 passed.
- Real-browser E2E: 6/6 passed.
- Linting issues: 0.
- Contract drift: clean.
- Production dependency audit: 0 vulnerabilities at configured threshold.
- Builds: Next production build, backend package, and PowerShell parse checks passed.

## Unresolved Questions

None.
