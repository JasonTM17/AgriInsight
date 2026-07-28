# Field Ledger alert-panel design rationale

Date: 2026-07-28

## Source of truth

- Existing product design: `D:\AgriInsight\plans\260719-0753-backend-auth-rbac\design-system\MASTER.md`.
- CK frontend-design workflow requires a deliberate visual direction and
  `ck:ui-ux-pro-max` analysis before implementation.

## Chosen direction

The alert center is a compact Field Ledger side panel, not a generic SaaS
notification drawer. Each row should let an operator answer three questions in
one scan: what transport condition is open, when was it last observed, and what
evidence/provenance is available. The header bell remains a small entry point;
the panel uses dense dividers and evidence rows rather than a wall of cards.

## Non-negotiable UI rules

- Be Vietnam Pro/Noto Sans and the existing green/harvest/neutral semantic
  token system remain authoritative; do not introduce a purple gradient or a
  separate design system.
- Use labels plus an icon for severity; color is never the only status cue.
- The button has an accessible name and an expanded state. The panel traps no
  focus, closes with Escape, restores focus to the bell, and supports keyboard
  acknowledgement.
- Render provenance: `Vận hành realtime`, policy label, last observation,
  processing freshness, and correlation/evidence identifiers only when the
  API explicitly permits them.
- Reserve layout-stable loading space. Empty, stale, denied, partial, and
  failed states name a safe next action. Refresh has a real pressed/loading
  state; the poll runs only while the panel is open.
- Validate at 375/768/1024/1440px and landscape. No content, focus ring, or
  action may be obscured by the app shell.

## Optional Stitch use

If a configured Stitch account and design review are available, generate one
private panel exploration in the plan-specific project and export its
`DESIGN.md`/image under `research/`. It is reference material only. Existing
Field Ledger tokens, semantic HTML, and runnable accessibility tests override
generated Tailwind/HTML.
