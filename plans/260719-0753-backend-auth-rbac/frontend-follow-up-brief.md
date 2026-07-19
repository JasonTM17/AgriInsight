# Frontend follow-up brief

Status: queued follow-on milestone; design direction approved for detailed planning, no frontend implementation in this backend phase.
Source: supplied AgriInsight specification plus CK UI/UX Pro Max design-system, agriculture color, typography, chart, and UX searches run on 2026-07-19.
Canonical artifacts: [`design-system/MASTER.md`](./design-system/MASTER.md) and its page overrides.

## Why this is separate

The existing Streamlit dashboard remains the tested local/internal analytics UI. A production web application needs the backend's stable OpenAPI, OIDC, tenant, permission, and scope contracts before it can implement authentication or role-aware mutations safely. Visual discovery can proceed now; production code starts only after backend phases 1-3 freeze those contracts.

## CK FE workflow

1. `ck:ui-ux-pro-max`: persist the master design system and page overrides; validate palette, typography, chart choice, responsive behavior, and accessibility.
2. `ck:frontend-design`: create low-fidelity flows, then a high-fidelity desktop/mobile prototype using realistic Vietnamese agricultural data; run the anti-slop audit before approval.
3. `ck:frontend-development` + `ck:web-frameworks`: implement a single `web/` Next.js App Router application in React/TypeScript. Do not introduce Turborepo until a second JavaScript application or genuinely shared package exists.
4. `ck:react-best-practices`: audit Server/Client Component boundaries, request waterfalls, bundle splitting, rendering, and table/chart performance.
5. `ck:test` + `ck:web-testing`: Vitest/component tests, Playwright role journeys, visual baselines, keyboard/screen-reader checks, responsive checks, and Core Web Vitals budgets.
6. `ck:code-review` -> `ck:ship`: security/UX review, documentation sync, small conventional commits, protected image release.

## Product direction

Concept: **Field Ledger / Agricultural Operations Desk**. The interface should feel like a precise operating console grounded in farm geography and seasonal rhythms, not a generic SaaS card wall. Thin field-row dividers, restrained topographic/plot-line texture, tabular figures, harvest-gold emphasis, and contextual maps create recognition without decorative clutter.

Design dials:

| Dial | Target | Interpretation |
|---|---:|---|
| Design variance | 5/10 | Asymmetric overview composition and map/insight panels; predictable forms, tables, and operational flows. |
| Motion intensity | 3/10 | 150-250 ms state feedback and purposeful drill transitions only; no perpetual animation; reduced motion is first-class. |
| Visual density | 7/10 desktop, 4/10 mobile | Dense analytical workspace on desktop; progressive disclosure and simplified chart/table views on small screens. |

## Provisional design system

### Color

Light-first because users may work in bright office/field conditions. Dark mode is not accepted until every semantic state and chart passes an independent contrast/visual-regression suite.

| Token | Value | Use |
|---|---|---|
| `brand-primary` | `#15803D` | Navigation identity, primary action, selected state; white foreground. |
| `brand-strong` | `#14532D` | High-emphasis text/brand surfaces. |
| `accent-harvest` | `#A16207` | Single product accent for attention/harvest context; white foreground. |
| `surface-canvas` | `#F7FAF8` | App background; avoids a saturated green wash. |
| `surface-panel` | `#FFFFFF` | Work surfaces, dialogs, tables. |
| `text-primary` | `#14261A` | Primary copy. |
| `text-muted` | `#526159` | Secondary copy after contrast verification. |
| `border-subtle` | `#DCE7DF` | Dividers and table structure. |
| `danger` | `#B42318` | Destructive/error semantics with icon and text. |

Charts use a separate color-blind-safe categorical scale plus line styles/patterns. UI state never relies on red/green or color alone. Every chart has a nearby text summary, keyboard-reachable values, a sortable table alternative, units, locale-aware formats, and CSV export where useful.

### Typography and iconography

- `Be Vietnam Pro` is the primary variable UI typeface with `Noto Sans` and system sans fallbacks. Use 500/600 weights for hierarchy and `font-variant-numeric: tabular-nums` for KPIs and tables; do not turn every heading into code-like monospace.
- Sentence case, Vietnamese-first copy, specific recovery messages, and realistic farm/season/material names. No lorem ipsum, “Acme”, round placeholder metrics, or marketing clichés.
- Use one reviewed SVG icon family with consistent stroke/optical size, then add small custom field/season glyphs only where they improve recognition. Icons never replace labels on primary navigation or critical actions.

### Layout and component language

- Desktop: persistent 240-272 px navigation rail, compact global filter/header, 12-column content grid, consistent 4/8 px spacing, 1 px dividers, and fewer generic bordered cards. Maximum content width is intentional per screen, not globally stretched.
- Mobile: one column, no accidental horizontal page scroll, 44 px minimum targets, filter sheet with active-filter summary, table-to-priority-card fallback, and no hidden critical action behind hover.
- Forms keep visible labels, field-level recovery text, error summary/focus management, optimistic version conflict recovery, loading/double-submit protection, and unsaved-change guard.
- Loading uses layout-stable skeletons after 300 ms. Empty, partial, stale, permission-denied, offline, and failed states each explain the next safe action.
- Motion animates only transform/opacity where practical; no blur in scrolling regions, arbitrary z-index, layout-shifting hover, custom cursor, neon glow, or decorative chart animation.

## Information architecture

| Area | Primary job | Core views |
|---|---|---|
| Overview | Executive decision scan | Revenue/cost/profit/yield/risk KPIs, time trend, farm ranking, profit waterfall, evidence-backed insight queue. |
| Farms | Compare and operate fields/seasons | Farm/field map, season status, yield/cost per hectare, target variance, drill-down to activity/harvest lineage. |
| Work | Plan and record field activity | Assigned work, activity status, append-only logs/corrections, workforce picker for authorized managers. |
| Inventory | Control stock and expiry | Warehouse scope, balances/lots, FEFO risks, receipts/issues/reversals, ABC analysis and supplier-safe view. |
| Costs | Explain spend without double counting | Operating-cost ledger, scoped summaries, farm/season/category drill-down, explicit cost-lens labels. |
| Crop health | Monitor evidence and risk | Moisture/weather/disease trends, plot heatmap, evidence image lineage, alerts; realtime/ML clearly marked deferred. |
| Data quality | Trust the numbers | Freshness/completeness/quarantine/reconciliation, manifest/run lineage, issue ownership and export. |
| Administration | Manage tenant access | User lifecycle, fixed role grants, farm/warehouse/activity assignments, audit-friendly confirmations. |

Navigation is derived from the enriched principal for clarity, but the UI is never the authorization boundary. The backend remains deny-by-default; hidden or disabled navigation cannot grant access. Deep links retain filter context and still handle a server-side 403/404 safely.

## Technical boundary

- One `web/` Next.js/React/TypeScript application, App Router, Server Components by default, isolated Client Components for interactive tables, maps, and charts.
- Generate typed request/response contracts from the checked-in OpenAPI artifact; never hand-copy backend DTOs. Validate untrusted server responses at the boundary where generated types alone cannot prove runtime shape.
- Recommended browser posture: Next.js BFF/session using Authorization Code + PKCE, encrypted `HttpOnly`/`Secure`/`SameSite` cookies, explicit CSRF protection for state changes, and no access/refresh token in browser storage. Finalize only after the production OIDC provider/session requirements are known.
- Cache only non-sensitive, scope-keyed reads. User/tenant/permission changes invalidate session-derived navigation; no shared cache key may omit tenant, profile, permission version, filters, or locale.
- The web runtime reads the backend API and approved analytics endpoints; it does not calculate canonical KPI business logic or write Python artifacts.

Primary framework references checked on 2026-07-19: [Next.js App Router](https://nextjs.org/docs/app), [Server and Client Components](https://nextjs.org/docs/app/getting-started/server-and-client-components), [Backend for Frontend](https://nextjs.org/docs/app/guides/backend-for-frontend), [authentication](https://nextjs.org/docs/app/guides/authentication), and [standalone Docker deployment](https://nextjs.org/docs/app/getting-started/deploying). Exact framework and package versions are resolved and pinned only when the detailed frontend plan starts.

## Acceptance budgets

- WCAG 2.2 AA: keyboard-only operation, visible focus, skip link, semantic landmarks/headings/tables, screen-reader announcements, contrast, reduced motion, and zoom/text scaling.
- Responsive fixtures: 375, 768, 1024, and 1440 px plus landscape; no page-level horizontal overflow.
- Core Web Vitals target at p75 on representative production-like data: LCP <= 2.5 s, INP <= 200 ms, CLS <= 0.1. Route bundle and query-waterfall budgets are measured and ratcheted after the first baseline.
- Lists above 50 visible rows are paged or virtualized; charts aggregate/downsample dense series and never render an unbounded result.
- Playwright role journeys cover tenant admin, executive, farm manager, inventory manager, analyst, field worker, and supplier-denied navigation. Visual tests cover loading/empty/error/permission/conflict states, not only happy-path screenshots.

## Docker image handoff

The frontend milestone creates a multi-stage, non-root `web/Dockerfile` using the production standalone output. The protected release workflow publishes `${DOCKERHUB_NAMESPACE}/agriinsight-web` with the same semantic-version/Git-SHA, OCI metadata, SBOM/provenance, vulnerability policy, and exact-digest smoke contract as the Python/backend images. No credentials, `.next/cache`, source maps intended to remain private, local `.env`, or test artifacts enter the runtime layer.

## Entry gate for the detailed frontend plan

- Backend phases 1-3 have a stable OpenAPI/Auth/RBAC contract and representative error fixtures.
- User confirms OIDC provider/BFF session direction, Vietnamese-only vs bilingual launch, and Docker Hub namespace/visibility.
- Design phase produces one approved master system plus page-specific overrides for Overview, Farms, Inventory, Costs, Crop Health, Data Quality, and Administration.
- No implementation begins from polished dashboard screenshots alone; each screen has role, task, data source, loading/error/empty behavior, drill path, and acceptance criteria.

## Unresolved questions

- Is launch Vietnamese-only or bilingual Vietnamese/English?
- Which production OIDC provider and session lifetime/step-up policy should the BFF implement?
- Which Docker Hub namespace and repository visibility own the web image?
- Is an exact existing AgriCore visual language available to inherit, or should this Field Ledger direction become the new source of truth?
