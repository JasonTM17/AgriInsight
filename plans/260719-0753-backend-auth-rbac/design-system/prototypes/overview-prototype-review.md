# Overview Field Ledger prototype review

Status: design prototype approved for contract handoff; not production frontend code.

## Artifacts

- [`overview-field-ledger-prototype.html`](./overview-field-ledger-prototype.html)
- [`overview-field-ledger-foundation.css`](./overview-field-ledger-foundation.css)
- [`overview-field-ledger-components.css`](./overview-field-ledger-components.css)
- [`overview-field-ledger-responsive.css`](./overview-field-ledger-responsive.css)
- [`overview-field-ledger-prototype.js`](./overview-field-ledger-prototype.js)

The prototype has no third-party runtime dependency, external image, token, API call, or duplicated KPI calculation. It presents a frozen design fixture derived from approved Gold artifacts.

## Data provenance

| Prototype evidence | Source fixture |
|---|---|
| Revenue, cost, profit, margin, production, area, season/risk counts | `artifacts/gold/executive_summary.csv` |
| 2025 profit line | `artifacts/gold/monthly_financials.csv` |
| Farm ranking | `artifacts/gold/farm_performance.csv` |
| Field 1.1 sensor and risk evidence | `artifacts/gold/field_health_status.csv` |
| Inventory, season, cost action queue | `artifacts/gold/insights.json`, `inventory_alerts.csv` |

The eventual web application must consume API/approved analytics contracts; it must not copy these values into production source or recompute canonical KPIs in the browser.

## CK FE checks

| Check | Evidence | Result |
|---|---|---|
| Field Ledger direction | Asymmetric 7/5 desktop, field-row dividers, plot evidence, restrained harvest accent | PASS |
| Anti-slop | No purple gradient, equal three-card row, generic English data, emoji icon, neon glow, centered hero, or decorative motion | PASS |
| Responsive | Browser renders at 375, 768, 1024, 1440 px; `scrollWidth === innerWidth` at each width | PASS |
| Touch targets | Visible `button`, `a`, and `select` controls are at least 44 × 44 px at all tested widths | PASS |
| Keyboard/AT navigation | Skip link; semantic landmarks/headings; mobile rail is `inert`/hidden when closed and isolates the workspace when open | PASS |
| Filter dialog | Native modal, visible labels, 16 px selects, Escape close, labeled close/reset/apply actions | PASS |
| Chart access | SVG title/description, text legend, explicit units/time scale, and a table fallback | PASS |
| Color access | Key semantic colors on white range from 4.92:1 to 9.11:1; status also uses text/icon | PASS |
| Reduced motion | `prefers-reduced-motion` disables transitions/animation; no perpetual animation | PASS |
| Runtime health | Browser console and page error logs empty; JavaScript syntax check passes | PASS |

## Interaction evidence

- Mobile rail exposes only its navigation while open, closes with explicit control or Escape, and restores focus to the menu trigger.
- Decision evidence rows update `aria-expanded` and reveal/hide their associated evidence block.
- Modal filters expose three labeled selects and two explicit completion paths.
- Chart and plot-map evidence remain understandable without hover.

## Known entry gates

- Be Vietnam Pro must be bundled/subset with `font-display` and verified Vietnamese glyph metrics in the production frontend; the standalone prototype intentionally uses local fallbacks.
- API/Auth/RBAC/error fixtures remain owned by backend phases 1-3. This prototype cannot authorize production `web/` implementation.
- Playwright visual baselines, screen-reader runs, Core Web Vitals, real data loading states, and Docker image checks belong to the production FE milestone.
- Temporary review screenshots live only under ignored `artifacts/_tmp/ui-review`; they are not release assets or Docker images.

## Unresolved questions

- Vietnamese-only or bilingual launch.
- Final OIDC/BFF session direction.
- Exact AgriCore visual language, if an approved reference exists.
