# Farm performance prototype review

Status: read-only comparison and drill-down prototype approved for backend-contract handoff; not production frontend code.

## Artifacts

- [`farm-performance-prototype.html`](./farm-performance-prototype.html)
- [`farm-performance-components.css`](./farm-performance-components.css)
- [`farm-performance-responsive.css`](./farm-performance-responsive.css)
- [`farm-performance-fixtures.js`](./farm-performance-fixtures.js)
- [`farm-performance-prototype.js`](./farm-performance-prototype.js)
- Shared shell foundation: [`overview-field-ledger-foundation.css`](./overview-field-ledger-foundation.css)

The prototype has no third-party runtime dependency, external image, API call, token, browser storage, production mutation, or geographic-map claim. It is a frozen design fixture; the plot board communicates field selection without inventing unverified boundaries.

## Data provenance and fixture boundary

| Prototype evidence | Verified source |
|---|---|
| Six farm names/codes, cultivated/harvested area, harvest quantity, operating profit, yield, cost/ha, and margin | `artifacts/gold/farm_performance.csv` |
| Twenty-four field codes/names, crops, area, moisture, last reading, risk status/score, and recommended action | `artifacts/gold/field_health_status.csv` |
| Per-farm season-alert counts, 12 total alerts, and five affected farms | `artifacts/gold/risk_alerts.csv` |

An automated fixture-to-Gold comparison found 6 farms, 24 fields, 12 alerts, and zero value mismatches. The eventual web application must consume approved API/analytics contracts; it must not copy these fixture values into production source or calculate canonical KPIs in the browser.

The current farm aggregate has no crop or season dimensions. The reviewed filter therefore offers only farm and metric choices; it does not fabricate crop/season filters. Master-data lifecycle, geographic field geometry, activity/harvest lineage, and authorized edits remain Phase 4 contracts.

## CK FE checks

| Check | Evidence | Result |
|---|---|---|
| Field Ledger direction | Comparison ledger, restrained harvest accent, plot evidence, tabular measures, and few bounded surfaces | PASS |
| Anti-slop | No centered hero, purple gradient, equal feature-card row, stock image, emoji icon, neon glow, or decorative animation | PASS |
| Responsive | Browser renders at 375, 768, 1024, and 1440 px plus 812 × 375 landscape with `scrollWidth === innerWidth` | PASS |
| Touch targets | Zero visible links, buttons, selects, summaries, or tabbable controls below 44 × 44 px at tested widths | PASS |
| Metric comparison | Profit, yield, margin, and cost/ha use explicit units; cost sorts low-to-high and states that a longer bar means lower cost | PASS |
| Chart alternative | All six farms and all measures remain available in a keyboard-openable semantic table with horizontal containment | PASS |
| Selection and history | Farm, field, and metric live in the URL; selection updates the paired views; browser Back restores exact prior state | PASS |
| Mobile plot flow | Inline atlas becomes a labeled “Xem sơ đồ” modal; field evidence returns to the selected plot, then to the invoking control; final breakpoint focus routing passed source review | PASS |
| Navigation/dialog | Mobile rail isolates the workspace with `inert`; Escape and explicit close restore focus; filter fields have visible labels | PASS |
| Keyboard | Metric tabs implement roving focus with Arrow Left/Right, Home, and End; selected rows/plots expose pressed state | PASS |
| Color access | Tested semantic foreground/background pairs range from 4.92:1 to 9.11:1; white/dark dual focus ring remains visible across light and brand surfaces | PASS |
| Motion and zoom | Reduced-motion transitions resolve to `0.01ms`; 200% browser-zoom and text-only scaling probes have no horizontal overflow or clipped plot text | PASS |
| Offline/runtime | Local fixture reloads offline; JavaScript syntax checks pass; browser console and page error logs are empty | PASS |

## Interaction evidence

- Arrow Right changes profit ranking to yield; End changes it to cost/ha, where Miền Đông ranks first at the lowest cost.
- Selecting Cao Nguyên binds its four fields and chooses the first actionable field; selecting another field updates evidence and URL without losing the metric.
- Browser Back restores field, farm, and metric selections instead of resetting the comparison.
- Invalid or inherited-property URL values normalize to the default allowlisted farm/field/metric state without a runtime error.
- Applying only a metric preserves the selected field; repeated selection does not add duplicate history entries. Cancel/Escape drafts reset on reopen and never replay an old dialog return value.
- The mobile rail focuses its close control, hides the workspace from interaction, and returns focus to the menu trigger.
- The mobile map opens the selected field evidence, closes the map while evidence is active, then reopens and focuses the same field on return.
- Crossing the 640 px map breakpoint closes/transfers the modal and focuses the equivalent visible inline plot or evidence action.
- Native details exposes the full data table by keyboard; table overflow is contained rather than becoming page overflow.

Browser checks exercised the mobile-to-desktop map and nested-evidence paths. The final desktop-to-mobile evidence-return branch received focused source review after the workstation disk guard stopped further browser sessions; the production Playwright gate must exercise both breakpoint directions.

## Known entry gates

- Backend Phase 4 must freeze farm, field, season, activity, harvest-lineage, authorization, optimistic-version, and audit contracts before production implementation.
- Real farm/field geometry and approved mapping policy are required before replacing the plot board with a geographic map.
- Loading, empty tenant/filter, partial analytics, stale cutoff, permission-denied, version-conflict, and API-failure fixtures remain required before production React acceptance.
- Season/crop/status filtering starts only when a source contract supplies those dimensions; the UI must not infer them from current aggregates.
- Playwright visual baselines, automated accessibility tooling, screen-reader runs, real-device safe-area checks, and Core Web Vitals belong to the production frontend milestone.
- Temporary review screenshots live only under ignored `artifacts/_tmp/ui-review`; they are not release assets or Docker images.

## Unresolved questions

- Should the first production map use exact field polygons, centroid markers, or a non-geographic plot ledger?
- Which farm comparison target supplies yield/cost variance: season plan, farm budget, or a tenant benchmark?
- Which roles may see precise coordinates and field-level sensor evidence across assigned farms?
- What cutoff-age threshold changes farm analytics from current to stale?
