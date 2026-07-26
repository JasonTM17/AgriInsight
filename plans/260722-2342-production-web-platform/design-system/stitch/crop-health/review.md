# Crop Health Stitch evidence

Status: accepted as composition evidence; rejected as production code.

## Source

- Stitch project: `9084754434575632570` (`AgriInsight/backend-auth-rbac`)
- Field Ledger design system: `assets/c1989dfbbef24da0a3d2617a620edb8a`, version `1`
- Screen: `1a7416a59341407dac977680001b73ea`
- Title: `Sức khỏe cây trồng - AgriInsight`
- Canvas: `2560 × 2048`
- Evidence image: `design.png`
- Evidence SHA-256: `565249ade15eb4f769a6c21a4304a3e804d8be011073c2066633fef903f3a7dc`
- Generation session: `1082141406920810166`
- Review edit session: `14002464233985248920`

## Accepted direction

- Field Ledger rail, Vietnamese-first labels, restrained green/ochre semantics,
  asymmetric risk/evidence layout, and tabular observation ledger.
- Primary decision is visible: inspect `Lô Lúa 1.1` next.
- Status combines text and icon; units and timestamps are visible.
- Crop evidence is explicitly labelled `Ảnh minh họa do AI tạo — chỉ dùng cho
  demo` and is not treated as a production observation.

## Implementation blockers

- The Stitch composition still shows `Vụ cà phê 2026` while the content is rice
  in `Vụ Hè Thu 2026`; production shell must take scope from the server-owned
  route context.
- The subtitle claims real-time analysis; production copy must state the
  synchronized freshness timestamp instead.
- The Stitch photo is not a runtime asset. Use the canonical reviewed visual
  catalog and preserve the demo-evidence boundary.
- Add explicit confidence/provenance and no-evidence recovery states in React.

## Handoff rules

Rebuild with semantic Next.js components. Do not copy Stitch HTML, `href="#"`,
CDN assets, embedded image exports, or illustrative KPI values into runtime.
