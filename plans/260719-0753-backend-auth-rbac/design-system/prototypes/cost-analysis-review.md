# Cost Analysis prototype review

Status: Gold-backed read-only reconciliation desk approved for design and backend-contract handoff; not production frontend code.

## Artifacts

- [`cost-analysis-prototype.html`](./cost-analysis-prototype.html)
- [`cost-analysis-components.css`](./cost-analysis-components.css)
- [`cost-analysis-responsive.css`](./cost-analysis-responsive.css)
- [`cost-analysis-fixtures.js`](./cost-analysis-fixtures.js)
- [`cost-analysis-view.js`](./cost-analysis-view.js)
- [`cost-analysis-prototype.js`](./cost-analysis-prototype.js)
- Shared shell: [`overview-field-ledger-foundation.css`](./overview-field-ledger-foundation.css)
- Browser evidence: [`cost-analysis-browser-gate`](../../reports/cost-analysis-browser-gate/)

The fixture has no external runtime, network request, browser storage, token, backend mutation, or file-generating export. Its export dialog is a disabled preflight contract until an authorized analytics endpoint exists.

## Data provenance and boundaries

| Prototype evidence | Verified source |
|---|---|
| Operating total VND 266,980,248,000, material/labor split, revenue, profit, margin, budget variance, and cost/kg | `artifacts/gold/cost_summary.csv` |
| Nineteen monthly operating rows, seven activity drivers, six farm comparisons, and 48/48 reconciled records with zero delta | `artifacts/gold/cost_monthly.csv`, `cost_activity.csv`, `cost_farm.csv`, and `cost_reconciliation.csv` |
| Procurement spend VND 41,631,673,900 across 648 transactions, eight suppliers, six warehouses, and seven months | `artifacts/gold/procurement_summary.csv` and `procurement_detail.csv` |
| Run and cutoff lineage | run `synthetic-2026-07-18-20260718`, cutoff `2026-07-18`, pipeline version `1` |

The prototype exposes exactly two non-combinable lenses: Operating P&L and Procurement spend (non-P&L). Inventory value remains outside the Cost total. Browser code validates and formats precomputed values; it does not recalculate canonical allocation, profit, margin, or reconciliation.

## Design and security decisions

- Lens, unit, cutoff, and source lineage stay visible in KPIs, trends, drivers, comparison rows, and export preflight.
- URL state is allowlisted to `lens` plus the selected activity or supplier. Unknown, malformed, impossible, and mismatched values canonicalize without retaining attacker-controlled parameters.
- Fixture schema checks reject blank text, impossible dates/timestamps, duplicate IDs, invalid defaults, and oversized trend, driver, comparison, or export collections.
- Empty driver collections are legitimate and do not invent a selected record. Invalid or unavailable fixtures render a non-partial unavailable state.
- The mobile navigation rail isolates the workspace with `inert` and `aria-hidden`; breakpoint changes move focus to an equivalent visible control.
- Charts use text, units, exact values, and table/list alternatives. Color is supplementary.

## Static gates

| Check | Result |
|---|---|
| JavaScript syntax for fixture, view, and controller | PASS |
| Fixture schema regression | PASS: valid fixture plus missing/impossible timestamp, missing/blank province, impossible procurement date, invalid default, empty drivers, oversized trend, duplicate ID, and missing fixture |
| Gold row counts and summary reconciliation | PASS |
| URL normalization, history-safe selection, IDs, selectors, and focus restoration source paths | PASS |
| External runtime, unsafe DOM sinks, browser storage, broad transitions, and debt markers | PASS: none found |
| Source modularization | PASS: each source file remains below 200 lines |
| Independent production-readiness review | PASS: zero Critical, High, or Medium findings after fixes |

## Browser and interaction gate

| Check | Evidence | Result |
|---|---|---|
| Responsive layout | 375, 768, 1024, and 1440 px plus 844 x 390 landscape; document width never exceeds viewport | PASS |
| Lens data | Operating shows 4 KPIs, 19 trend rows, 7 drivers, and 6 comparisons; Procurement shows 4 KPIs, 7 trend rows, 8 suppliers, and 15 materials | PASS |
| URL and history | Malformed/unknown parameters normalize; Back and Forward restore exact lens and selection | PASS |
| Keyboard and focus | Arrow/Home/End tabs, driver refocus, mobile rail, scrim, Escape, dialog focus trap, and return focus | PASS |
| Touch and mobile navigation | Visible interactive targets are at least 44 x 44 px; rail/workspace isolation and breakpoint focus transfer pass | PASS |
| Export preflight | Dialog exposes scope, cutoff, row estimate, and disabled generation action; no download occurs | PASS |
| Motion and zoom | Reduced-motion transitions resolve to zero; 200% browser zoom has no document overflow | PASS |
| Offline/runtime | Local-file reload uses only fixture resources; browser console and page-error logs are empty | PASS |
| Print | Four-page tagged PDF includes the full material table and omits navigation, scrim, skip link, and JavaScript | PASS |

Screenshots cover Operating desktop/mobile/landscape, Procurement desktop/tablet, export preflight, print, and 200% zoom. These files are review evidence, not product assets or Docker images.

## Review adjudication

| Finding | Verdict | Evidence after fix |
|---|---|---|
| Mobile rail left hidden content reachable or could retain focus across breakpoints | Accepted and fixed | `inert`/`aria-hidden` parity plus desktop-to-mobile focus transfer passed browser retest |
| Invalid fixture dates, blank labels, missing province, defaults, or unbounded rows could reach rendering | Accepted and fixed | Eleven-case schema regression and explicit row caps pass |
| Unknown URL parameters survived canonicalization | Accepted and fixed | Canonical URL contains allowlisted state only |
| Live-region text was visually exposed | Accepted and fixed | Status remains announced through a visually hidden live region |
| Skip link and navigation leaked into print | Accepted and fixed | Rendered PDF text and visual inspection confirm both are absent |

## Production entry gates

- Backend Phase 6 freezes the operating-ledger, procurement-read, reconciliation, authorization, pagination, and export contracts.
- Loading, stale, permission-denied, partial reconciliation, API-failure, and export-failure fixtures are approved.
- Production web code consumes scoped APIs, proves tenant/role denial paths, and never ships this static fixture as a data source.
- Playwright, automated accessibility tooling, screen-reader checks, production-like performance budgets, and real-device safe-area checks pass before release.

## Unresolved questions

- Which operating-cost comparison is canonical in production: approved budget, prior period, or both?
- Which roles may view supplier identity, unit price, and procurement detail across warehouses?
- What reconciliation delta blocks export rather than marking it as provisional?
- Which backend endpoint owns the signed, auditable export and its retention policy?
