# Inventory control prototype review

Status: source-complete and static-verified; browser, responsive, keyboard, zoom, offline, and visual approval pending.

## Artifacts

- [`inventory-control-prototype.html`](./inventory-control-prototype.html)
- [`inventory-control-components.css`](./inventory-control-components.css)
- [`inventory-control-responsive.css`](./inventory-control-responsive.css)
- [`inventory-control-fixtures.js`](./inventory-control-fixtures.js)
- [`inventory-control-view.js`](./inventory-control-view.js)
- [`inventory-control-prototype.js`](./inventory-control-prototype.js)
- Shared shell: [`overview-field-ledger-foundation.css`](./overview-field-ledger-foundation.css)

## Data provenance and scope

| Prototype evidence | Verified source |
|---|---|
| Ten assigned-warehouse alerts and exact recommended actions | `artifacts/gold/inventory_alerts.csv`, filtered to `WH-001` |
| Fifteen WH-001 balances, thresholds, days of supply, nearest expiry, value, and order recommendation | `artifacts/gold/inventory_status.csv`, filtered to `WH-001`; scoped shares are deterministic display ratios over these values |
| Enterprise ABC class only, never enterprise totals or values | `artifacts/gold/inventory_abc.csv`, cross-checked by material code |
| Matching source receipt/batch/supplier and latest movement evidence | `artifacts/silver/inventory_transactions.csv`, `suppliers.csv` |

Automated fixture reconciliation found 10 alerts, 15 scoped ABC-labelled value rows, 15 WH-001 SKU-locations, and zero mismatches. Severity split is 3 critical, 5 warning, and 2 watch.

The source receipt whose expiry matches the Gold nearest-expiry value is lineage evidence only. Silver does not expose remaining balance or quality by batch, so the prototype never calls it a current FEFO lot. Production lot actions remain blocked until Phase 5 supplies the authoritative contract.

## Design and security decisions

- Scope is fixed to assigned warehouse `WH-001`; the DOM and client fixture exclude enterprise totals, cross-warehouse values, and cross-warehouse location counts.
- Alert queue groups by text-labelled severity and required action. Color is supplementary.
- Negative/zero, below-reorder, in-range, and above-target balances receive explicit text, exact quantity/unit, and an accessible gauge description.
- Inventory value and procurement context are labelled separately from operating cost.
- Evidence dialog is read-only. There is no receipt, issue, reversal, retry, or browser-side KPI calculation.
- URL state is allowlisted for severity, type, material, and alert. Assigned warehouse is deliberately absent from client-controlled URL state in this single-warehouse fixture.
- Mobile source collapses to priority rows and a labelled evidence dialog with safe-area padding; runtime verification is pending.

## Static gates

| Check | Result |
|---|---|
| JavaScript syntax for fixture, view, and controller | PASS |
| Fixture-to-Gold/Silver reconciliation | PASS — zero mismatches |
| URL selection regression | PASS — alert-only, material-only, matching pair, mismatched pair, and filtered-out material |
| ARIA/ID references | PASS — 27 IDs, 33 references, no missing/duplicate ID |
| JavaScript data selectors | PASS — no unresolved selector |
| External runtime, unsafe DOM sinks, browser storage, focus suppression, broad transition, debt markers, and cross-warehouse payload fingerprints | PASS — none found |
| Source files under the 200-line modularization threshold | PASS |

A debugger audit found that an unsupported `warehouse` query was silently overwritten with `WH-001`. The finding was accepted and fixed by removing warehouse from prototype URL state; the future production URL may carry warehouse only after server-side authorization resolves permitted scope.

The adversarial source review found two more boundary defects. Enterprise summary and ABC values were embedded despite the WH-001 claim, so they were replaced with warehouse-scoped values and enterprise ABC class only. A valid material-only deep link was ignored unless an alert ID was also present, so URL selection now resolves either a validated alert pair or a material-only selection before canonicalization.

## Review adjudication

| Finding | Verdict | Evidence after fix |
|---|---|---|
| Unsupported warehouse query was silently overwritten | Accepted and fixed | Prototype URL has no warehouse read/write path |
| Enterprise totals and ABC values crossed the WH-001 client boundary | Accepted and fixed | Payload fingerprint gate plus scoped-value reconciliation pass |
| Material-only URL selection was ignored | Accepted and fixed | Five-case pure Node URL regression passes |

Three bounded independent code-reviewer attempts produced no final result and were discarded. The CK sequential fallback completed spec, quality-checklist, and adversarial passes, but no independent-review PASS is claimed. Browser-dependent behavior remains explicitly outside this source review.

## Browser gate pending

Do not promote this fixture to reviewed until the disk guard passes and browser evidence covers:

- 375, 768, 1024, and 1440 px plus 812 × 375 landscape with no page overflow;
- keyboard tabs, queue selection, filter cancel/apply, native details, both dialogs, layered Escape, and focus restoration;
- URL history/back plus malformed and inherited-property-like values;
- empty filter combinations, negative stock, overstock, missing days of supply, and missing batch evidence;
- 200% browser zoom and text-only scaling, reduced motion, offline reload, print, touch targets, and console/page errors;
- final desktop/mobile screenshots and contrast inspection.

## Production entry gates

- Backend Phase 5 freezes warehouse/material/lot/movement authorization, idempotency, optimistic-version, audit, and conflict contracts.
- Loading, empty warehouse, no-alert, stale, permission-denied, conflict/replay, and save-failure fixtures are approved.
- Production web tests use representative tenant/warehouse assignments and prove denied deep links do not leak foreign counts.

## Unresolved questions

- Which API owns authoritative remaining balance and quality status per lot for FEFO?
- Which roles may see supplier identity and unit cost across assigned warehouses?
- What movement-age threshold blocks a replenishment or issue action as stale?
