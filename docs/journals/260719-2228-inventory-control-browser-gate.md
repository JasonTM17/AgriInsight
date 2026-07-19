# Inventory Control browser gate milestone

**Date**: 2026-07-19 22:28
**Severity**: Medium
**Component**: Inventory Control design prototype
**Status**: Resolved

## What Happened

We promoted the WH-001 Inventory Control fixture from source-reviewed to browser-approved. The read-only screen now renders ten warehouse alerts, fifteen ABC-labelled SKU-location rows, balance evidence, batch lineage, filter/history state, responsive navigation, and print output without inventing a production mutation path.

The browser pass exposed defects that static review did not prove away: hidden empty states were still laid out, breakpoint focus could land in inert content, print lost the ABC table, clearing filters discarded focus, and browser history could leave a stale evidence dialog open. Fixture handling also assumed structurally valid input and could throw or freeze on malformed/oversized arrays.

## The Brutal Truth

The screen looked finished before it was trustworthy. That was frustrating because every defect lived in a boundary users notice immediately—focus, history, empty data, print, or warehouse scope—while the happy path stayed polished.

The independent reviewer found three Medium blockers and no Critical/High issues. Its re-review later exhausted the agent service quota, so claiming an independent PASS would be dishonest. We used the CK sequential fallback, found two more edge cases, fixed them, and reran mechanical and browser evidence.

## Technical Details

- Fixture reconciliation remains `10` alerts and `15` WH-001 SKU-location rows with zero foreign-warehouse payload.
- Static gate: `30/30` PASS; adversarial fixture gate: `11/11` PASS.
- Source limits: controller `158` lines, view `180` lines.
- Browser viewports: `375`, `768`, `1024`, `1440`, `812 × 375`, and `844 × 390`; no page-level overflow and no visible target below `44 px`.
- Resilience: `200%` browser zoom, `200%` text scaling, reduced motion, local/offline resource reload, native details, dialogs, Back/Forward, malformed URL state, and empty/negative/overstock/missing-evidence cases.
- Print: three rendered pages retain all `10` alert and `15` ABC rows, repeat headers, and restore disclosure state after repeated print events.
- Console/page errors: none in the final focused run.

Disk pressure briefly became the most dangerous failure mode. Windows expanded the C-drive pagefile while browser and Java processes were active. We compressed safe caches, moved Playwright/Maven/WinGet caches to ignored D-drive storage through junctions, and relocated inactive browser profiles instead of deleting user data. Final guard: C `18.541 GB`, D `34.190 GB`, both PASS.

## Bugs That Mattered

1. CSS layout rules overrode `hidden`, rendering real and empty evidence simultaneously.
2. Mobile/desktop rail transitions could retain focus inside hidden or inert navigation.
3. Print styling exposed navigation chrome and omitted collapsed table content.
4. Clearing an empty filter hid the focused control without a valid destination.
5. Empty history state left the evidence modal open with stale material fields.
6. Malformed or huge fixtures could throw on `.filter` or spread a large ABC array into `Math.max`.
7. Alert IDs were interpolated into a CSS selector; special characters could raise `SyntaxError`.
8. Repeated `beforeprint` events could overwrite the original disclosure state.

## Decisions and Rejected Alternatives

- Keep scope fixed to `WH-001` and reject a foreign fixture. A client-controlled warehouse parameter was rejected because server-side authorization does not exist yet.
- Treat matching receipt/batch data as lineage evidence, not an authoritative remaining FEFO balance.
- Cap prototype input at `50` alerts and `100` ABC rows. Production lists above the UI budget require pagination or virtualization, not unbounded rendering.
- Show an explicit unavailable state for invalid data. Rendering partial KPIs from a broken fixture was rejected because a plausible wrong number is worse than no number.
- Keep receipt, issue, reversal, and replenishment mutations absent until backend Phase 5 freezes authorization, idempotency, optimistic-lock, and audit contracts.

## Lessons Learned

- `hidden` is a behavioral contract; component CSS must not accidentally override it.
- Focus restoration needs a valid fallback after both rendering and breakpoint changes.
- Fixture validation must protect domain scope and reconciliation, not only JavaScript types.
- Print is a separate product surface and needs state-lifecycle tests, not only CSS inspection.
- Disk gates need free-space and memory/pagefile observation; free space can collapse even when repository output stays on D.

## Next Steps

- Commit source hardening, browser evidence, and milestone documentation as separate conventional commits.
- Run the guarded backend unit gate with Maven repository/temp/home on D; keep Docker verification blocked unless the daemon and storage gates pass.
- Keep Inventory production work queued for backend Phase 5 and frontend implementation queued for the stable Auth/RBAC/OpenAPI boundary.

## Unresolved Questions

- Which API owns authoritative remaining balance and quality status per lot for FEFO?
- Which roles may see supplier identity and unit cost across assigned warehouses?
- What movement-age threshold blocks replenishment or issue actions as stale?
