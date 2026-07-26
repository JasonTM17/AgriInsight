# Code Review — Phase 7 Inventory UI Slice

Commits: 9b8b21a, b2c8102, 993445a, 2103399. Scope: inventory route tree, feature components, api client, mutation hook, navigation delta, tests. Compared against work sibling + `phase-07-inventory-control.md`. Read-only review; no gates run (controller evidence: typecheck clean, lint 0, vitest 208/9 skip; E2E gate in flight).

## Scope
- ~2,180 added LOC across 4 commits (16 new files, 2 nav files, 3 test files)
- Focus: fabricated-client-behavior audit, mutation/idempotency lineage, states, a11y/mobile, E2E soundness, nav delta, permission sourcing

## Overall Assessment

Server-truth discipline is genuinely good: no client re-sorting anywhere, no ABC/FEFO/threshold recomputation, analytics rendered verbatim from a strict envelope with warehouse-scope enforcement, mutations schema-locked server-side. The 993445a expiry fix is a correct contract-deference move with a covering test. However, two High defects ship in this slice: the filter form's primary action is broken for default/partial filter values (sibling divergence, untested), and the idempotency hook rotates keys on ambiguous 5xx responses — a regression against the Work pattern that reopens duplicate-ledger-entry risk.

## Critical Issues

None.

## High Priority

### H1. Filter form "Áp dụng" yields invalid-link panel for default/empty optional filters
- `inventory-filter-bar.tsx:23` — GET form; unselected selects/dates submit empty strings: `?warehouseId=X&materialId=&kind=&from=&to=`.
- `inventory-route-state.ts:73-77` — empty string fails: `if (rawKind !== undefined && !kindSchema.safeParse(rawKind).success) return null;` ("" is not undefined, fails enum); same for `from`/`to` iso-date checks.
- Result: any submit without ALL of kind+from+to populated → `parseInventoryRouteState` null → whole page replaced by "Liên kết tồn kho không hợp lệ" failed StatePanel (`page.tsx:31-42`).
- Sibling handles this correctly: `work-route-state.ts:54-56` (`rawStatus ? statusSchema.safeParse(...) : undefined`) and `:64` (`rawSearch || undefined`) treat "" as absent.
- Zero coverage: contract test only round-trips fully-populated state (`inventory-control.contract.test.ts:266-284`); E2E never clicks "Áp dụng". Classic happy-path phantom gap.
- Fix: normalize "" → undefined in `scalar()` or per-field before validation, mirroring work.

### H2. Idempotency key rotated on ambiguous 5xx → duplicate ledger entry on retry
- `use-idempotent-inventory-mutation.ts:44-46` — on `!result.ok`, resets `fingerprint`/`idempotencyKey` before setting error feedback.
- `inventory-api-client.ts:40-41` — every non-2xx (including 502/504 where BFF→Spring timed out AFTER Spring committed; `toInventoryMutationResponse` maps unknown upstream failures to 502) becomes `ok:false`.
- Retry of the identical receipt/issue payload after such a response mints a NEW key (`:30-33`) → Spring idempotency dedupe bypassed → double-post into an append-only ledger.
- Work sibling deliberately does NOT reset on failure: `use-idempotent-work-mutation.ts:36-42` keeps fingerprint+key, so identical-payload retry replays the same key. Inventory diverges from the Phase 6-validated pattern with no stated rationale.
- E2E only exercises the network-throw path (`route.abort` → fetch rejects → catch at `:59-65` retains key), so this hole is invisible to the passing suite.
- Fix: remove the reset at `:45-46` (fingerprint change already rotates the key when the payload changes).

## Medium Priority

### M1. 10,000-offset cap NOT disclosed like Work; pager dead-ends at boundary
- `inventory-operational-tables.tsx:175-177` — "Sau →" rendered on `hasMore` alone; at offset 10,000 the link targets 10,050, which `inventory-route-state.ts:43-52` rejects → invalid-link panel.
- Work caps and discloses: `work-page-controls.tsx:23` (`canLoadNext = hasMore && offset + limit <= WORK_MAX_OFFSET`) and `:41` ("Đã đạt giới hạn máy chủ").
- Verification item "cap disclosed like Work does" fails. Requires ~200 pages to hit; still a broken-link path on the ledger table, which is the table most likely to grow.

### M2. If-Match architecture does not match the stated design (claim drift, not a security hole)
- Claim under review: "client must NOT send If-Match itself; handled server-side via refetch." Actual: client sends it — `inventory-api-client.ts:33` attaches `If-Match`; `inventory-reversal-form.tsx:53-61` passes a client-fetched ETag; BFF REQUIRES the header (`reversals/route.ts:27,39-49`, 400 without it).
- Security holds: ETag originates from an authenticated server GET (`getInventoryTransactionEtag`, format-validated `inventory-api-client.ts:60`), and BFF refetches + compares before forwarding (`post-inventory-reversal.ts:17-24`, mismatch → 409). A guessed/stale value cannot win; only the current server version passes.
- Consequence worth knowing: backend bumps the source version on reversal (`PostgresInventoryLedger.java:88-92`), so an ambiguous reversal that actually committed can never be replayed via retained key — BFF 409s ("Hãy tải lại") before Spring's idempotent replay is reached. Safe (no double-apply) but the hook's "retry keeps the same key" promise is unreachable for reversals; error copy steers to reload, which is the right recovery.
- Action: correct the design record, or move If-Match attachment fully into the BFF; do not leave the claim and code disagreeing.

### M3. Analytics truncation not disclosed where server reports more
- Loader pins `limit: 50, offset: 0` (`load-inventory-view-model.ts:114-117`); envelope carries `page.hasMore`/`total` (`inventory-analytics-contract-schema.ts:115-120`) — never rendered. `StatusTable` label "{items.length} dòng trong trang Gold" (`inventory-analytics-panels.tsx:156`) is page-accurate but silent about further rows.
- `AbcPanel` header says "{abc.length} nhóm vật tư" (`:123`) while rendering `abc.slice(0, 8)` (`:129`) with no "top 8" note — count and rows visibly disagree once >8 groups exist.
- No invented totals confirmed (nothing summed/recomputed client-side); this is a disclosure gap only. Surface `page.hasMore`/`total` and label the ABC cut.

## Low Priority

- L1. No `:focus-visible` rules in `inventory-control.module.css` (grep: zero matches); globals cover only `a, button, input` (`globals.css:41-43`), so the forms' `select`/`textarea` fall back to UA outlines. Work styles all three (`work-operations.module.css:93-96`). Off-convention, not broken.
- L2. `inventory/loading.tsx` hand-rolls a `degradedPanel` div instead of the shared `StatePanel state="loading"` used by work (`work/loading.tsx`). Has `aria-busy` and real copy; consistency nit.
- L3. `InventoryPreparationError.ambiguous` is always constructed `false` at both sites (`inventory-api-client.ts:57,62-64`) and never read — dead field, remove.
- L4. Analytics 403 (e.g. FARM_MANAGER without analytics scope) renders "Phân tích tạm thời gián đoạn" (`inventory-analytics-panels.tsx:25`) — permanent denial framed as transient. Requirement itself is met: operational sections stay live and the partial notice renders (`inventory-control-page.tsx:87-91`; covered by `inventory-control.contract.test.ts:361-375`).
- L5. Hand-built URL `?warehouseId=X&materialId=` passes parse ("" skips the uuid gate, `inventory-route-state.ts:71`) and `compactQuery` only strips `undefined` (`inventory-generated-client-adapter.ts:272-277`) → literal empty `materialId=` sent upstream; sections fail closed. Harmless, but same root cause as H1.

## Verification Matrix (task items)

1. No fabricated client behavior — PASS with M1/M3 caveats. No `sort` param anywhere in the feature (grep clean); items rendered in server order verbatim (`inventory-operational-tables.tsx:58,81,105`; `inventory-analytics-panels.tsx:94,129,171`); `severityClass`/badges map server strings/booleans, no recomputation; heading even states the no-resort invariant (`:43`).
2. Mutation forms — PASS. `receiptPayload`/`issuePayload` (`inventory-transaction-form.tsx:164-202`) map 1:1 onto `postInventoryTransactionSchema` (`.strict()` + kind-partitioned refinements, `inventory-mutation-contract.ts:24-43`); no extra/defaulted fields (`reasonCode` injected BFF-side, `post-inventory-transaction.ts:17-19`). Supplier/material selects fed from server catalogs, canManage-gated (`load-inventory-view-model.ts:134-136`, `inventory-control-page.tsx:72-79`). Reversal lineage server-fetched, never guessed (M2 for the claim mismatch).
3. Hook — fingerprint = [path, payload, ifMatch] (`use-idempotent-inventory-mutation.ts:29`) ✓ cross-target reset ✓; in-flight guard (`:28`) ✓; reload only on 2xx (`:56-57`) ✓; H2 rejection-path regression ✗.
4. States — PASS (H1 aside). StatePanel for denied/failed/no-warehouse; Vietnamese-first copy throughout; empty states real (`inventory-operational-tables.tsx:148`); no raw-color anti-pattern (tokens + few literal hexes, same as work module).
5. A11y/mobile — PASS with L1. Labels wrap controls (E2E getByLabel proves binding); `tableScroll` overflow-x + breakpoints to 35rem; E2E asserts scrollWidth ≤ innerWidth at 375px (`inventory-control.spec.ts:27-37,49,98,119`).
6. E2E — PASS. All selectors/testids exist in components (verified each). Replay flow sound: `route.fetch()` commits the 201 upstream then aborts delivery (`:58-63`) → client catch retains key → retry asserts same wire key (`:81-83`) and exactly 1 row post-reload (`:84`) — validates real server dedupe, not phantom. Denied journey asserts generic copy + no `access_token|refresh_token|Bearer` in body (`:117-118`).
7. Navigation delta — PASS. href → `/inventory`, active-key branch inserted before the `/protected` fallback, no other entries touched (b2c8102 diff); tests added (`navigation.test.ts:39,52-59`). No residual `module=inventory` links in src (grep clean).
8. page.tsx — PASS. Fresh per-request identity (`loadPlatformPageContext` React-cache + `dynamic = "force-dynamic"`, permissions from backend /me via `authorization-context.ts:33-50`); UI canManage is display-only — BFF re-checks INVENTORY_MANAGE (`inventory-api-security.ts:34`). Picker redirect only when a warehouse exists (`page.tsx:63-64`), redirect outside try/catch (NEXT_REDIRECT not swallowed), picker unreachable once warehouseId present (`load-inventory-view-model.ts:72-74`) → no loop. Foreign warehouse denied (`page.tsx:73-81`); foreign material fails closed even on catalog fetch failure (`load-inventory-view-model.ts:82-91`).

## Recommended Actions

1. H1 — normalize empty-string query values to undefined in `parseInventoryRouteState`, mirroring `work-route-state.ts:54-64`; add a contract test feeding `{ warehouseId, materialId: "", kind: "", from: "", to: "" }` (the exact browser form output).
2. H2 — drop the fingerprint/key reset on `!result.ok` in `use-idempotent-inventory-mutation.ts:45-46`; add a unit test: 502 response → resubmit identical payload → same Idempotency-Key on second call.
3. M1 — port the `canLoadNext` cap + "Đã đạt giới hạn máy chủ" disclosure into `Pager`.
4. M2 — reconcile design record with implementation (or move If-Match fully server-side).
5. M3 — render `page.hasMore`/`total` on the status table; label ABC as top-8.
6. L1-L5 as convenient.

## Metrics
- Controller-supplied: typecheck clean, lint 0 warnings, vitest 208 pass / 9 skip (not re-run per read-only constraint)
- New test surface: 21 contract cases (inventory-control), +3 route-security negatives, 2 E2E journeys, 2 nav assertions

## Unresolved Questions

1. E2E determinism: `toHaveCount(1)` (`inventory-control.spec.ts:84`) assumes the E2E inventory persona's warehouse ledger is empty at test start. If the guarded gate reuses a persistent stack across runs, rows accumulate and the journey fails on rerun. Confirm the gate reseeds per run.
2. Spring idempotency replay status for a duplicate transaction POST (same key, same payload): assumed 2xx replay; if Spring returns 409 for replays, the E2E retry step would be red — the running gate will answer this.
3. Whether the `/protected?module=inventory` legacy mapping in `getActiveNavigationKey` should be retired now that the real route exists (no in-repo links remain; external bookmarks would land on the placeholder with correct nav highlight).
