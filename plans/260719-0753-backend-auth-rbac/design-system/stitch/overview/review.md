# Stitch Overview design review

Status: **Accepted as design evidence; rejected as production code**

- Generated: 2026-07-22
- Stitch project: `AgriInsight/backend-auth-rbac` (`9084754434575632570`)
- Screen: `6e9785d6ec1f4d5fb5eace03444c1a55`
- Device prompt: desktop, 1440 x 1024

## Artifacts

- [`design.png`](./design.png) — generated visual reference.
- [`design.html`](./design.html) — generated static Tailwind prototype; reference only.
- [`DESIGN.md`](./DESIGN.md) — machine-extracted structure; subordinate to the project [`MASTER.md`](../../MASTER.md).

## Accepted direction

- Field Ledger identity is recognizable through the field-row line, restrained topographic texture, agricultural green, harvest ochre, and tabular figures.
- Persistent labeled navigation and the asymmetric overview composition fit a dense agricultural operations desk.
- Vietnamese-first content uses contextual farms, plots, seasons, quantities, and recovery-oriented actions instead of generic placeholder copy.
- The main scan supports a concrete decision path: KPI status, seasonal trend, farm comparison, profit bridge, evidence summary, then next safe action.

## Production blockers in the generated export

- Navigation uses dead `href="#"` links and does not implement route state, deep links, or predictable back behavior.
- The document lacks a skip link, complete landmark labels, chart summaries/table alternatives, keyboard-reachable chart values, and robust focus management.
- Several supporting labels are 10–14 px and all-caps; production body and input text must follow the readable type scale in `MASTER.md`.
- The layout is desktop-only. Production must provide explicit 375, 768, 1024, and 1440 px behavior with no page-level horizontal overflow.
- Tailwind, Google Fonts, and Material Symbols are loaded from public CDNs; production dependencies and fonts must be pinned, optimized, and covered by the asset policy.
- Inline script mutates presentation directly and does not honor `prefers-reduced-motion`; production interaction belongs in isolated, tested components.
- Loading, empty, stale, partial, denied, offline, conflict, and failure states are described by the master system but are not implemented in this static export.
- Generated KPI values are illustrative design content. Production must consume scoped API/analytics contracts and must not recompute canonical business metrics in the browser.

## Handoff rules

1. Treat `MASTER.md` and the page override as normative; this Stitch export supplies composition evidence only.
2. Rebuild with semantic React/Next.js components; do not copy the inline Tailwind runtime or script into production.
3. Preserve the accepted visual identity while meeting WCAG 2.2 AA, reduced motion, responsive, Core Web Vitals, and role/scope gates.
4. Validate every chart with a text summary and accessible table alternative.
5. Run CK frontend design, development, React performance, web testing, and code review gates before accepting the page.

## Decision

Use the generated composition as the visual starting point for the future `web/` Overview route. Do not ship or containerize this export.

## Unresolved questions

- Production OIDC provider and BFF session policy remain deployment inputs.
- Docker Hub namespace and repository visibility remain release inputs.
