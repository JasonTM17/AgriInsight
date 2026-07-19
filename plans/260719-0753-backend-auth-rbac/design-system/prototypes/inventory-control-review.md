# Inventory control prototype review

Status: source-complete, static-verified, browser-approved, and CK sequential review PASS; production integration remains gated by backend Phase 5.

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
- Mobile source collapses to priority rows and a labelled evidence dialog with safe-area padding; browser verification covers portrait, landscape, breakpoint changes, modal focus, zoom, and large text.

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
| Fixture boundary adversarial matrix | PASS — 11/11 for missing arrays, duplicate keys, non-finite/range errors, foreign warehouse, invalid dates, inconsistent counts, orphan material, and row caps |

A debugger audit found that an unsupported `warehouse` query was silently overwritten with `WH-001`. The finding was accepted and fixed by removing warehouse from prototype URL state; the future production URL may carry warehouse only after server-side authorization resolves permitted scope.

The adversarial source review found two more boundary defects. Enterprise summary and ABC values were embedded despite the WH-001 claim, so they were replaced with warehouse-scoped values and enterprise ABC class only. A valid material-only deep link was ignored unless an alert ID was also present, so URL selection now resolves either a validated alert pair or a material-only selection before canonicalization.

## Review adjudication

| Finding | Verdict | Evidence after fix |
|---|---|---|
| Unsupported warehouse query was silently overwritten | Accepted and fixed | Prototype URL has no warehouse read/write path |
| Enterprise totals and ABC values crossed the WH-001 client boundary | Accepted and fixed | Payload fingerprint gate plus scoped-value reconciliation pass |
| Material-only URL selection was ignored | Accepted and fixed | Five-case pure Node URL regression passes |
| Clearing an empty filter discarded focus | Accepted and fixed | Focus moves to the selected queue row; browser result `MAT-UREA-stockout` |
| Browser history could leave stale evidence dialog content open | Accepted and fixed | Empty popstate closes the dialog, hides stale content, and focuses the active severity tab |
| Malformed or oversized fixtures could throw or freeze rendering | Accepted and fixed | Schema/range/uniqueness/scope reconciliation, 50-alert/100-ABC caps, iterative maximum, and explicit unavailable UI pass |
| Fixture alert IDs entered a dynamic CSS selector | Sequential review finding; fixed | Row lookup now compares `dataset.alertId` without selector interpolation; special-character probe does not throw |
| Repeated `beforeprint` could overwrite original disclosure state | Sequential review finding; fixed | Two `beforeprint` events followed by `afterprint` restore both disclosures to their original closed state |

The independent reviewer produced the three Medium findings above with zero Critical/High findings. Its bounded re-review attempt later hit the agent service quota before returning a verdict. CK sequential fallback therefore repeated the critical and informational passes, added the selector/print findings above, ran 30/30 static checks, 11/11 adversarial fixture checks, and reran focused browser regressions. No independent re-review PASS is claimed; the recorded sequential gate is PASS with zero remaining Critical/High/Medium findings.

## Browser gate

Evidence: [`reports/inventory-control-browser-gate/`](../../reports/inventory-control-browser-gate/).

| Browser check | Result |
|---|---|
| Responsive layout | PASS — 375, 768, 1024, 1440, 812 × 375, and 844 × 390; zero page overflow and zero visible targets below 44 px |
| Keyboard and focus | PASS — severity roving tabs, queue selection, filter cancel/apply/clear, native details, rail/dialog Escape, breakpoint focus routing, and dialog restoration |
| URL/history boundary | PASS — Back/Forward restoration; alert-only, material-only, mismatched pair, malformed, inherited-property-like, unknown, and unsupported warehouse values canonicalize safely |
| Data edges | PASS — empty filter, negative/zero balance, overstock, missing days supply, missing batch evidence, invalid fixture unavailable state, and capped fixture boundary |
| Accessibility/resilience | PASS — 200% browser zoom, 200% text scaling, reduced motion, local/offline resource reload, touch targets, ARIA hidden-state parity, and no console/page errors |
| Print | PASS — disclosures restore after repeated print events; rendered PDF has three pages with all 10 alert and 15 ABC rows, repeating headers, and no rail/skip-link leakage |
| Visual evidence | PASS — desktop, tablet, mobile, both landscapes, evidence dialog, zoom, large-text, and print screenshots inspected |

## Production entry gates

- Backend Phase 5 freezes warehouse/material/lot/movement authorization, idempotency, optimistic-version, audit, and conflict contracts.
- Loading, empty warehouse, no-alert, stale, permission-denied, conflict/replay, and save-failure fixtures are approved.
- Production web tests use representative tenant/warehouse assignments and prove denied deep links do not leak foreign counts.

## Unresolved questions

- Which API owns authoritative remaining balance and quality status per lot for FEFO?
- Which roles may see supplier identity and unit cost across assigned warehouses?
- What movement-age threshold blocks a replenishment or issue action as stale?
