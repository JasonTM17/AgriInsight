# Design guidelines

The queued web application follows the CK FE **Field Ledger** direction: a precise agricultural operations desk grounded in plots, seasons, evidence, and Vietnamese-first copy. It is intentionally separate from the existing Streamlit analytics MVP until backend auth, tenant scope, RBAC, and OpenAPI contracts stabilize.

## Source of truth

- [Master design system](../plans/260719-0753-backend-auth-rbac/design-system/MASTER.md)
- [Frontend follow-up brief](../plans/260719-0753-backend-auth-rbac/frontend-follow-up-brief.md)
- Page overrides live beside the master under `plans/260719-0753-backend-auth-rbac/design-system/pages/`.

## Non-negotiable quality gates

- WCAG 2.2 AA, keyboard-first operation, visible focus, semantic chart/table alternatives, reduced motion, and no color-only status.
- Responsive fixtures at 375/768/1024/1440px plus landscape; no page-level horizontal overflow.
- p75 budgets: LCP ≤2.5s, INP ≤200ms, CLS ≤0.1; lists over 50 visible rows are paged or virtualized.
- No browser token storage, no client-side canonical KPI recomputation, no hidden authorization in navigation, and no unproven image publication.

Production UI implementation starts only after the frontend entry gate in the follow-up brief is satisfied. The current turn creates design artifacts only; it does not pretend that backend contracts or image release are ready.
