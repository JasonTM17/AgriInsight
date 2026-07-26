# Code Review — Phase 7 inventory-control server slice (committed)

Date: 2026-07-26. Scope: commits e1411b1, 5c97fa0, 6438d88, ecec052, 299a633, 6034dc0 at HEAD.
Out of scope: uncommitted UI (`web/src/app/(platform)/inventory/`, `components/`, `inventory-api-client.ts`, `use-idempotent-inventory-mutation.ts`, `inventory-format.ts`) — later pass. Accepted deviations honored (no trends payload, per-section SourceResult degrade, web-only mandatory warehouse, no ledger demo seed).

## Verdict

No Critical/High findings. Trust-boundary layering, If-Match discipline, allowlist exactness, seed idempotency and reconciliation fail-closed all verified against code. 3 Medium, 6 Low.

## Checklist verification (evidence)

1. **Mutation trust boundaries** — PASS.
   - Ordering: `authorizeWorkMutation` = trusted host → same-origin → CSRF → session → idempotency key (`web/src/features/work/work-api-security.ts:42-62`). `authorizeInventoryMutation` layers INVENTORY_MANAGE on top (`inventory-api-security.ts:28-41`) and both routes authorize BEFORE body read (`transactions/route.ts:16-18`, `reversals/route.ts:24-28`).
   - If-Match strong-ETag regex `^"\d{1,19}"$` validated at route (`reversals/route.ts:16,27,39-49`) and again pre-fetch in the client (`upstream-client.ts:101-112`); weak `W/"7"`, bare `7`, 20-digit rejected by test (`inventory-route-security.contract.test.ts:222-239`).
   - Bounded body: Content-Type + Content-Length precheck + 64KB streamed cap + fatal UTF-8 (`work-api-security.ts:71-138`); upstream serialize re-capped at 64KB (`upstream-client.ts:114-123`).
   - No upstream body relay on !ok: status-mapped generic problem, upstream body never read (`inventory-mutation-response.ts:38-48`); proven by test (`inventory-route-security.contract.test.ts:290-304`). Success body strict-parsed against generated contract + ETag format enforced or 502 (`inventory-mutation-response.ts:50-61`).
   - No token/tenant leakage: problem shape fixed to type/title/status/code/correlationId (`work-api-security.ts:140-161`).

2. **GET [transactionId]** — PASS with note.
   - INVENTORY_READ enforced (`inventory-api-security.ts:44-69`). No web-layer `assertVisibleWarehouse`, but Spring `findById` applies tenant + warehouse scope SQL (`PostgresInventoryLedger.java:34-39` via `WarehouseScopeSql.append`) and out-of-scope → `ResourceNotFoundException` → uniform 404 (`InventoryReadService.java:32-37`). Foreign vs missing indistinguishable — no probe oracle. Consistent with the accepted "server scope enforcement prevents leakage" deviation.
   - Returns strict-parsed `InventoryTransactionResponse` fields only + strong ETag + no-store (`[transactionId]/route.ts:20-27`, schema `inventory-generated-contract-schemas.ts:102-130`). Nothing beyond generated contract.
   - Transaction POST to a foreign warehouse UUID → 403 `scope_denied` uniformly whether or not the warehouse exists (checked against visible list only, `inventory-api-security.ts:71-83`) — no existence oracle.

3. **upstream-client If-Match** — PASS. `inventoryTransactionReversal` has `requiresIfMatch: true` (`allowed-operation.ts:262-268`); missing/malformed throws before fetch (`upstream-client.ts:62-65,108-110`). `inventoryTransactionPost` and both work mutations have no flag → any supplied ifMatch throws (`upstream-client.ts:105-107`). Work callers pass no ifMatch (`submit-work-log.ts:16`, `correct-work-log.ts:16`). Validation precedes fetch (line 62 vs 85). Tests: `upstream-client.test.ts:186-244`, including "no If-Match header on work mutations" (`:152`).

4. **Allowlist exactness** — PASS. All five inventory read ops + two mutations match generated `schema.d.ts` param sets exactly (balances `:4113-4119`, lots `:4146-4153`, transactions `:4180-4188`, byId `:4254-4256`, catalogs `:4321-4325/:4733-4737/:5438-5442`; analytics inventory `analytics/schema.d.ts:1462-1466`). No invented params, no `sort=fefo`. See L3 for allowlisted-but-unused params.

5. **load-inventory-view-model** — PASS. Foreign warehouse rejected at `load-inventory-view-model.ts:75-78` before any operational/analytics fetch (test asserts exactly 1 upstream call, `inventory-control.contract.test.ts:344-359`). Visibility solely from `warehouseCatalog` (`inventory-generated-client-adapter.ts:79-90`). Analytics query uses validated `selectedWarehouse.code` (`:117`) and result re-checked for foreign rows, throws on mismatch (`inventory-analytics-contract-schema.ts:136-149`). Date mapping `from→T00:00:00Z`, `to→T23:59:59.999Z` (`:234-240`); inversion impossible — `from > to` rejected at parse (`inventory-route-state.ts:78`). Masters (supplier catalog) fetched only when `canManage` (`:134-136`).

6. **Zod vs Spring shape rules** — PASS with M1/L1. Receipt requires supplierId+unitCostVnd+batchCode+expiryDate, prohibits stockLotId+reason; issue requires reason, prohibits supplier/cost/batch/expiry; both `.strict()` (`inventory-mutation-contract.ts:24-121`) — exact mirror of `InventoryTransactionPostRequest.java:86-114`. Injected `reasonCode` WEB_*_ENTRY matches Spring pattern `[A-Z][A-Z0-9_]{0,79}`. Reversal body {quantityBase, reason} + injected reasonCode matches `InventoryReversalRequest.java`. Length caps identical (64/500/128).

7. **Demo seed + reconciliation** — PASS. Supplier upsert one-to-one from `silver/suppliers.csv` (`demo_tenant_master_sql.py:63-71`), codes SUP-001..008 all satisfy V12 `suppliers_code_canonical` uppercase pattern and names (Vietnamese diacritics, no quotes, ≤160, nonblank) satisfy `suppliers_display_name_nonblank` (`V12__create_inventory_tables.sql:53-69`); identical rows in `artifacts/{silver,big-data,smoke}`. `literal()` escapes quotes; NaN name would render NULL and fail loudly on NOT NULL — no silent constraint bypass (`demo_tenant_sql_primitives.py:23-24,60-69`). Idempotent: deterministic uuid5 id + `ON CONFLICT (tenant_id, code) DO UPDATE` (`:35-57`). Reconciliation: suppliers added to producer domains + expected catalog (`demo_tenant_reconciliation.py:16-24,64-67`), inspection SQL (`demo_tenant_inspection_sql.py:42-44`), and gate requires exact 8-domain set, zero errors, count equality — old 7-domain reports now 503 fail-closed (`reconciliation_gate.py:12-23,63-81`).

8. **Plan anti-patterns** — CLEAN. No fabricated If-Match anywhere (grep: If-Match only in inventory reversal path; forwarded value is the client's header, pre-verified against refetched ETag, `post-inventory-reversal.ts:17-33`). No `.sort(`/`toSorted` in committed inventory/work sources; order-preservation test at `inventory-control.contract.test.ts:392-420`. `hasMore` passed through verbatim; catalog collection loops to completion or throws at 10k offset — no silent truncation (`inventory-generated-client-adapter.ts:253-270`). Seed emits zero UPDATE against ledger/append-only tables (only master `ON CONFLICT DO UPDATE`).

## Findings

### Medium

- **M1 — Timezone-dependent, stricter-than-Spring receipt expiry rule.** `inventory-mutation-contract.ts:41-49` rejects receipts where `expiryDate < occurredAt.slice(0, 10)`. (a) Spring has no such rule (`InventoryTransactionPostRequest.java:86-100`; no expiry/occurredAt check in domain or V12) — web can reject a Spring-valid body, e.g. backdated posting of already-expired stock. (b) `occurredAt` accepts any offset (`z.iso.datetime({ offset: true })`), so the same instant passes with `+00:00` but fails with `+07:00` near midnight — validation outcome depends on client-chosen offset, not the business date. If the rule is intended, derive the comparison date from the UTC instant.
- **M2 — All upstream 409s collapse to "state changed, reload".** Spring emits 409 for both version conflicts and business conflicts such as insufficient stock (`ApiExceptionHandler.java:118-143`); `inventory-mutation-response.ts:28-31` maps every 409 to "Máy chủ đã có trạng thái khác. Hãy tải lại trước khi gửi." — misleading for insufficient stock (reloading won't help). Same for all 400 business rules → one generic sentence (`:12-14`). Consistent with the accepted work-phase sanitization posture; a whitelisted upstream problem-`code` relay (codes only, never detail text) would fix UX without leaking. Non-blocking.
- **M3 — GET-route negative tests missing.** `inventory-route-security.contract.test.ts` covers the mutation matrix thoroughly but has no case for: GET `[transactionId]` with a persona lacking INVENTORY_READ (403 path `inventory-api-security.ts:57-63`), upstream 404 → `inventory_not_found` mapping without body relay (`inventory-route-responses.ts:65-83`), or untrusted Host on the GET route. These paths are code-verified but regression-unprotected.

### Low

- **L1 — Double-precision loss at schema maxima.** `quantityBase` max 99_999_999_999_999.9999 and `unitCostVnd` max 9_999_999_999_999_999.99 (`inventory-mutation-contract.ts:5-15`) exceed IEEE-754 double significand; values near the caps silently round in `JSON.parse` before reaching Spring's BigDecimal. Also web allows 14 integer digits vs Spring's 16 (`@Digits(integer = 16)`). Practically irrelevant at demo magnitudes.
- **L2 — `occurredTo` day-boundary gap.** `T23:59:59.999Z` + Spring `occurred_at <= ?` (`PostgresInventoryTransactionQueries.java:48-49`) excludes timestamptz events in the final 999µs of the "to" day.
- **L3 — Allowlisted-but-unused query params.** `expiringBefore`, `includeDepleted` (lots, `allowed-operation.ts:186-194`) and `search` (catalogs `:219,232,238`) are contract-valid but never sent by any committed caller — surface broader than needed.
- **L4 — Catalog outage misreported as `foreign_material`.** `load-inventory-view-model.ts:82-92`: rejected materials catalog + materialId filter → `foreign_material` (user told their filter is out of scope when the catalog merely failed). Fail-closed direction is right; the state label lies.
- **L5 — Reversal body `transactionId` silently overridden by path param** (`reversals/route.ts:29-31`, spread order path-wins). Safe, but a mismatch could 400 instead of being masked.
- **L6 — Reconciliation compares suppliers on {code, active} only** (`demo_tenant_reconciliation.py:64-67`); display_name drift undetected until reseed self-heals via upsert. Consistent with all other domains — convention, not regression. Also: loader waterfall is 4 sequential rounds (warehouses → materials → 4-parallel → suppliers); materials/suppliers could join the parallel batch (`load-inventory-view-model.ts:71-136`).

## Positive observations (risk calibration)

- BFF adds an INVENTORY_MANAGE pre-check that work routes never had — strictly tighter than the phase 6 pattern; Spring remains authoritative (`WarehouseScopeSql.requireWriteScope`).
- Reversal pre-fetch double-checks the client ETag then still forwards If-Match upstream — TOCTOU window closed by Spring's precondition, BFF check only improves error determinism.
- `scopedOperationalSource` (`load-inventory-view-model.ts:207-222`) degrades a section if upstream ever returned cross-warehouse rows — leak becomes a failed panel, not rendered data.
- Tests assert absence of upstream calls, exact forwarded payloads/headers, and sanitization — not phantom coverage.

## Plan status

Phase 7 server-side slice (filter schema, loaders, mutation wrappers, allowlist, contract tests, supplier seed + reconciliation) is implemented and matches phase-07 requirements/data-flow steps 1-7. Outstanding for acceptance: UI route tree, `loading.tsx`, panels, E2E `@inventory` journey (uncommitted, later pass), and nav registration (serialized controller step). Recommend: fix M1 (or confirm intent), consider M2 code relay, add M3 tests alongside the UI pass. Phase file status remains `pending` — leave state changes to the lead.

## Unresolved questions

1. `canManage` provenance: loader trusts the flag (`load-inventory-view-model.ts:134`); committed code has no caller. UI pass must verify it derives from INVENTORY_MANAGE, not client input.
2. Is "expiry before receipt date" rejection an intended product rule (M1)? If yes, compute the receipt date from the UTC instant rather than `occurredAt.slice(0, 10)`.
3. Should insufficient-stock 409s be user-distinguishable from stale-version 409s via a whitelisted code relay (M2), or is uniform sanitization the accepted posture for this phase too?
