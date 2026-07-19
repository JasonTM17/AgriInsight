# AgriInsight Field Ledger — Master Design System

Status: design source of truth for the queued CK FE milestone. No production web code is implemented yet.

Generated: 2026-07-19
Method: `ck:ui-ux-pro-max --design-system` plus agriculture/color/typography/chart/UX searches, then product-specific synthesis.

## Product direction

AgriInsight is a precise agricultural operations desk. It should feel grounded in plots, seasons, and evidence: a field ledger with quiet structure, not a generic SaaS card wall. The memorable element is a living field-row line that connects a KPI, its evidence, and the next safe action.

Design dials:

| Dial | Target | Rule |
|---|---:|---|
| Design variance | 5/10 | Use an asymmetric overview/map split; keep forms and tables predictable. |
| Motion intensity | 3/10 | Use 150–250 ms transform/opacity feedback only; no perpetual motion. |
| Visual density | 7 desktop / 4 mobile | Dense analytical workspace on desktop; progressive disclosure on small screens. |

## Color tokens

Light-first for bright office and field conditions. Dark mode is a later milestone and cannot ship until each semantic state and chart passes an independent contrast review.

| Token | Value | Use |
|---|---|---|
| `--color-brand-primary` | `#15803D` | Navigation identity, primary action, selected state; white text. |
| `--color-brand-strong` | `#14532D` | High-emphasis text and brand surfaces. |
| `--color-accent-harvest` | `#A16207` | Harvest/attention emphasis; white text after contrast verification. |
| `--color-surface-canvas` | `#F7FAF8` | App background. |
| `--color-surface-panel` | `#FFFFFF` | Work surfaces, tables, dialogs. |
| `--color-text-primary` | `#14261A` | Body and heading copy. |
| `--color-text-muted` | `#526159` | Secondary copy only after contrast check. |
| `--color-border-subtle` | `#DCE7DF` | Field-row dividers and table structure. |
| `--color-info` | `#1D4ED8` | Neutral information state; never the brand gradient. |
| `--color-danger` | `#B42318` | Destructive/error state with icon and text. |

Charts use a separate color-blind-safe categorical scale with line styles or patterns. Red/green alone never communicates status. Every state has a text label and icon.

## Typography

- Display and UI: **Be Vietnam Pro**, variable weights 400/500/600/700.
- Fallback/body: **Noto Sans**, then system sans.
- KPI and table figures: `font-variant-numeric: tabular-nums`; use a compact mono face only for IDs, run hashes, and timestamps.
- Base body size is 16px; inputs are never below 16px on mobile. Body line-height is 1.5–1.65.
- Vietnamese-first sentence case. Use specific farm, season, material, and run names; never use lorem ipsum, “Acme”, or round placeholder metrics.

## Spatial system

- Spacing follows a 4/8px rhythm: 4, 8, 12, 16, 24, 32, 48.
- Desktop uses a 240–272px navigation rail, compact global filter bar, and a 12-column content grid. Content width is chosen per screen, not stretched indiscriminately.
- Mobile is one column with no page-level horizontal overflow. Wide tables use a deliberate scroll wrapper or priority-card fallback.
- Use 1px dividers, modest radius (8–12px), and inner tonal separation before adding shadows. Avoid equal three-card rows as the default composition.
- Reserve space for asynchronous content; do not use `100vh` where `100dvh` is appropriate.

## Component language

- One primary action per view. Labels remain visible beside icons in navigation and critical actions.
- Inputs have visible labels, helper text, field-level errors, and a recoverable error summary with focus management.
- Loading after 300ms uses layout-stable skeletons. Empty, stale, partial, denied, offline, conflict, and failed states each name the next safe action.
- Pressed/focused/disabled states never shift layout. Focus rings are visible and keyboard order follows reading order.
- Use one reviewed SVG icon family with consistent optical size and stroke. No emoji as structural icons.
- Motion uses transform/opacity, ease-out entry/ease-in exit, 150–300ms, and `prefers-reduced-motion` fallbacks.

## Data visualization

- Overview targets: bullet charts for KPI-vs-target groups; line charts for time trends; horizontal bars for farm/category comparison; waterfall only for a clearly explained profit bridge.
- Always show numerical values, units, date granularity, legend, and a nearby text summary. Tooltips supplement; they never contain the only value.
- Provide a sortable accessible table alternative. Keyboard focus must reach interactive chart elements. Aggregate or downsample dense series; never render an unbounded result.
- Export affordances must respect the backend/export contract and never create a second KPI calculation in the browser.

## Image and asset policy

- Prefer meaningful farm/plot evidence images with explicit provenance, dimensions, responsive `srcset`, WebP/AVIF, lazy loading below the fold, and descriptive alt text.
- Decorative imagery must be sparse and contextual (topographic/plot-line texture); no stock-photo hero collage, neon glow, or unlicensed generated asset.
- Runtime images are application assets, not database data. Container publication later includes only first-party AgriInsight images after scan, SBOM/provenance, and digest smoke checks; upstream PostgreSQL is never mirrored.

## Accessibility and performance budgets

- WCAG 2.2 AA: 4.5:1 normal text, 3:1 large text/UI boundaries, keyboard-only operation, skip link, semantic landmarks, labels, announcements, and zoom/text scaling.
- Test widths: 375, 768, 1024, 1440px plus landscape. No page-level horizontal overflow.
- Target p75: LCP ≤2.5s, INP ≤200ms, CLS ≤0.1 on representative data. Lists over 50 visible rows are paged or virtualized.
- Route-level code splitting, server components by default, no client-side token storage, and no shared cache key without tenant/principal/permission/filter context.

## Page overrides

Each page must define role, primary task, data source, loading/empty/error/denied/conflict states, drill path, and responsive fallback before implementation.

- [Overview](./pages/overview.md)
- [Farms](./pages/farms.md)
- [Inventory](./pages/inventory.md)
- [Costs](./pages/costs.md)
- [Crop health](./pages/crop-health.md)
- [Data quality](./pages/data-quality.md)
- [Administration](./pages/administration.md)

## Anti-slop gate

Reject a screen that defaults to a centered hero, purple/blue gradient, three equal feature cards, generic English placeholder data, emoji icons, decorative chart animation, hover-only actions, hidden focus, or a giant unbounded table. The screen must make a real agricultural decision easier within one scan.
