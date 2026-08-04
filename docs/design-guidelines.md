# Design guidelines

The Next.js web application follows the CK FE **Field Ledger** direction: a precise agricultural operations desk grounded in plots, seasons, evidence, and Vietnamese-first copy. It is intentionally separate from the existing Streamlit analytics MVP. Backend auth, tenant scope, RBAC, farm operations, Phase 5 inventory contracts, Phase 6 cost APIs, and Phase 7 outbox/release contracts are stable. Hosted browser acceptance and the current protected four-image release `v0.4.0` are complete; external production deployment approval remains a separate gate. Frontend work must consume the versioned OpenAPI contracts and preserve warehouse assignment and farm cost scope.

## Source of truth

- [Master design system](../plans/260719-0753-backend-auth-rbac/design-system/MASTER.md)
- [Frontend follow-up brief](../plans/260719-0753-backend-auth-rbac/frontend-follow-up-brief.md)
- Page overrides live beside the master under `plans/260719-0753-backend-auth-rbac/design-system/pages/`.

## Non-negotiable quality gates

- WCAG 2.2 AA, keyboard-first operation, visible focus, semantic chart/table alternatives, reduced motion, and no color-only status.
- Responsive fixtures at 375/768/1024/1440px plus landscape; no page-level horizontal overflow.
- p75 budgets: LCP ≤2.5s, INP ≤200ms, CLS ≤0.1; lists over 50 visible rows are paged or virtualized.
- No browser token storage, no client-side canonical KPI recomputation, no hidden authorization in navigation, and no unproven image publication.

The local Streamlit dashboard consumes the 8 contextual AI visuals under
`dashboard/assets/generated/` with contextual captions, a soft missing-file
fallback, and an explicit AI-generated demo-evidence boundary; the 14 hosted CI
screenshots stay separate under `docs/assets/screens/` as real UI evidence from
Actions run `30868766788`. The production-shaped Next.js implementation covers
nine permission-driven areas and passed the real seven-persona hosted browser
gate. Python, backend, web, and analytics API images have verified Docker
Hub/GHCR `0.4.0` evidence; production host, OIDC operations, hostname/TLS,
observability, and rollback approval remain gated.
