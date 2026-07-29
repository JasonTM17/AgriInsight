---
phase: 3
title: Live operations UX and acceptance
status: completed
effort: 1.5-2d
---

# Phase 3: Live operations UX and acceptance

## Overview

Priority: P1  
Current status: completed; hosted CI `30445148252` passed at feature head
`e8a02a2`, and PR #14 was rebase-merged at
`bd724503dd3e0864cbd546a6398216fbcd053f31`
Owner boundary: Next UI, browser verification, docs, hosted technical evidence

Replace the inert app-header bell with a focused, Field Ledger alert panel. It
renders only the typed BFF contract, retains source and freshness provenance,
and supports a guarded user acknowledgement without pretending that an
operational alert is a batch Gold insight or a live WebSocket stream.

## Acceptance evidence

- [Hosted CI run `30445148252`](https://github.com/JasonTM17/AgriInsight/actions/runs/30445148252)
  passed all 10 gates at feature head `e8a02a2`, including real
  PostgreSQL/Kafka, seven-persona browser, and candidate-image build checks.
- [PR #14](https://github.com/JasonTM17/AgriInsight/pull/14) was rebase-merged
  on `main` at `bd724503dd3e0864cbd546a6398216fbcd053f31`.
- Local typecheck, ESLint, and Vitest passed. The local heavy E2E run was not
  attempted because the disk guard measured the D drive below its 20 GiB floor;
  the guard was not weakened and hosted CI is the browser acceptance evidence.
- Candidate images were built and validated in CI without publishing a new
  image. External deployment remains owner-gated.

## Context links

- [Plan overview](./plan.md)
- [Phase 2 contract](./phase-02-exact-backend-alert-api-and-bff-contract.md)
- [Field Ledger design rationale](./research/field-ledger-alert-panel-design.md)
- [`D:\AgriInsight\docs\design-guidelines.md`](../../docs/design-guidelines.md)
- [`D:\AgriInsight\plans\260719-0753-backend-auth-rbac\design-system\MASTER.md`](../260719-0753-backend-auth-rbac/design-system/MASTER.md)

## Requirements

### Interaction and data presentation

1. Render the bell only when current server authorization includes
   `REALTIME_ALERT_READ`; do not treat nav visibility as authorization.
2. The panel is a labelled dialog/popover anchored to the bell. It supports
   Enter/Space, Escape, visible focus, focus restoration, screen-reader status
   announcements, and 44px-or-larger interactive targets.
3. Each row visibly labels `Vận hành realtime`, severity text/icon, policy,
   state, last observed timestamp, processing freshness, and safe evidence.
   It never rebrands a Gold batch alert as realtime.
   When the response sets `hasMore`, render it only as an informational bounded
   window note; v1 must not expose a load-more/history control.
4. Fetch the server-fixed latest-50 open-alert window on open, allow explicit refresh, and poll only while open at a bounded
   interval. Stop on close/unmount/denial; use abort/cancellation and do not
   run an always-on browser transport. Acknowledgement posts only `{}` with
   `credentials: "same-origin"`, the script-readable CSRF cookie echoed in
   `X-AgriInsight-Csrf`, and a caller-stable `Idempotency-Key`.
5. Provide layout-stable loading, no-alert, denied, stale, partial, failure,
   retry, and acknowledged/stale-acknowledgement states. Do not optimistically
   resolve an operational condition from the client.
6. Use a semantic source-labelled route/link for a longer view only if actual
   data/access needs it. The initial scope is a panel; no giant global
   notification page or duplicate navigation item.

### Visual system

- Preserve Field Ledger green/harvest/info/danger semantic tokens, Be Vietnam
  Pro/Noto Sans, 4/8px rhythm, compact evidence dividers, tabular dates/counts,
  and dense desktop/progressive mobile behavior.
- No new generic design system, gradient, hero, AI-generated image, emoji, or
  three-card notification wall. Use the existing reviewed SVG icon language.
- Motion is transform/opacity-only, 150–250ms, optional under
  `prefers-reduced-motion`, and never holds keyboard focus or blocks refresh.
- Validate 375/768/1024/1440px and landscape; no page-level horizontal
  overflow, clipped panel, or inaccessible off-screen action.

## Related code files

| Path | Action | Purpose |
|---|---|---|
| `D:\AgriInsight\web\src\components\app-shell\app-header.tsx` | Modify | Replace inert bell with permission-aware alert panel entry point. |
| `D:\AgriInsight\web\src\components\ui\icon.tsx` | Modify | Add reviewed severity glyphs to the existing SVG family only if needed. |
| `D:\AgriInsight\web\src\features\realtime-alerts\realtime-alert-contract.ts` | Reuse | Keep Phase 2 strict generated-contract schemas/types as the client boundary. |
| `D:\AgriInsight\web\src\features\realtime-alerts\realtime-alert-client.ts` | Create | Client-safe same-origin BFF fetch/acknowledgement wrapper with bounded error mapping, caller cancellation, `credentials: "same-origin"`, CSRF cookie/header, exact `{}`, and caller-owned idempotency. |
| `D:\AgriInsight\web\src\features\realtime-alerts\realtime-alert-panel-state.ts` | Create | Framework-free state, freshness, and idempotency-key logic testable in the Node Vitest environment. |
| `D:\AgriInsight\web\src\features\realtime-alerts\components\realtime-alert-entry.tsx` | Create | Small permission-gated client boundary that lazy-loads the panel after first open. |
| `D:\AgriInsight\web\src\features\realtime-alerts\components\realtime-alert-panel.tsx` | Create | Keyboard-accessible panel and fetch lifecycle. |
| `D:\AgriInsight\web\src\features\realtime-alerts\components\realtime-alert-row.tsx` | Create | Source-labelled evidence row/ack action. |
| `D:\AgriInsight\web\src\features\realtime-alerts\components\realtime-alert-panel.module.css` | Create | Field Ledger panel responsiveness/reduced-motion/focus styles. |
| `D:\AgriInsight\web\tests\features\realtime-alert-panel-state.test.ts` | Create | Node-runnable lifecycle, stale/partial/ack-retry, and no-leaked-poll state coverage. |
| `D:\AgriInsight\web\tests\contracts\alert-route-security.contract.test.ts` | Extend only if a UI-required BFF invariant is missing; do not duplicate Phase 2 route security coverage. |
| `D:\AgriInsight\web\tests\e2e\helpers\realtime-alert-database.ts` | Create | Test-only admin-database fixture inserts one valid open alert for the authenticated demo tenant and deletes it deterministically in cleanup. |
| `D:\AgriInsight\web\tests\e2e\realtime-alerts.spec.ts` | Create | Real persona, responsive, a11y, token/CSP, and accessibility browser proof. |
| `D:\AgriInsight\docs\system-architecture.md` | Modify | Alert source, worker/API/BFF boundaries, and strict deferrals. |
| `D:\AgriInsight\docs\data-contracts.md` | Modify | Alert feed/ack contract, provenance, pagination, and no-payload rule. |
| `D:\AgriInsight\docs\backend-development.md` | Modify | V22 grants/permissions/worker lifecycle and verification commands. |
| `D:\AgriInsight\docs\deployment-guide.md` | Modify | Worker config/rollback and real-host-only deployment handoff. |
| `D:\AgriInsight\docs\project-roadmap.md` | Modify | Accepted scope/evidence or remaining owner gates. |
| `D:\AgriInsight\docs\codebase-summary.md` | Modify | Verified implementation snapshot after acceptance only. |
| `D:\AgriInsight\plans\260728-realtime-operational-alert-center\reports\acceptance-YYYY-MM-DD-realtime-alert-center.md` | Create | Actual hosted/local evidence, rollback, open owner inputs. |

## Resolved implementation decisions

- `AppHeader` and `AppShell` remain Server Components. The server checks
  `REALTIME_ALERT_READ` and renders a small client entry component only for
  authorized users, passing primitive `canAcknowledge` data rather than the
  server-only authorization context or permission set.
- Phase 2's strict generated-contract schemas, BFF routes, cancellation, and
  route-security coverage are reused. The browser client calls only the
  same-origin BFF; its loader is explicitly client-safe and never imports a
  server-only helper. Acknowledgement mirrors the established client mutation
  transport: read only `__Host-agriinsight-csrf`, send it as
  `X-AgriInsight-Csrf` with `credentials: "same-origin"`, exact `{}`, and a
  caller-owned stable `Idempotency-Key`; it never reads auth/session/token data.
- The current Vitest environment is Node-only and has no React/jsdom test
  harness. Unit coverage therefore targets a framework-free lifecycle/state
  module; actual dialog semantics, focus restoration, keyboard interactions,
  responsive layout, and axe checks belong to the existing real-browser gate.
- The panel is a compact non-modal Field Ledger dialog anchored to the 44px
  bell: no focus trap, no unread badge, no history/load-more route, and no
  generated image or Stitch asset. It uses existing SVG icons and Field Ledger
  green/ochre/danger semantic tokens.
- Positive real-browser proof uses a Phase-3-owned fixture with the already
  guarded `AGRIINSIGHT_WEB_TEST_ADMIN_DATABASE_URL`. It inserts one valid open
  alert for the authenticated demo tenant, then deletes only that fixture row
  and its acknowledgement revisions in `finally`; it does not start the private
  worker, intercept the BFF/backend path, or alter production/runtime code.

## Implementation steps

1. Reuse the Phase 2 contract and model client state as
   `closed/loading/ready/stale/partial/failed/denied/acknowledging/acknowledged/
   acknowledgement-unknown`; unknown typed enum/data values fail closed.
2. Build a same-origin client wrapper and framework-free state module. Fetch
   only on open, refresh explicitly, poll only while open/visible with one
   request in flight, abort on close/unmount/denial, and retain a stable
   idempotency key for an unknown acknowledgement result. The POST wrapper must
   send exact `{}`, `credentials: "same-origin"`, `X-AgriInsight-Csrf` from the
   script-readable CSRF cookie, and no auth/session/token value.
3. Keep the app shell server-rendered. Replace the bell only for authorized
   users with a lazy client entry and a non-modal `role="dialog"` panel. Use
   Escape, outside pointer, route/unmount, and permission-loss cleanup with
   focus restoration when the trigger remains mounted.
4. Render Field Ledger evidence rows with explicit Vietnamese source, severity,
   policy, state, last observation, processing freshness, and permitted
   evidence/correlation identifiers. Acknowledgement stays server-confirmed;
   the row is never optimistically removed.
5. Implement layout-stable loading/no-alert/denied/stale/partial/failure/retry
   states, `aria-live="polite"` summaries, 44px controls, transform/opacity-only
   motion, and 375/768/1024/1440 plus landscape CSS without page overflow.
6. Add Node-runnable lifecycle/state tests and real-browser persona/a11y proof.
   The Phase-3 E2E fixture inserts and cleans up one valid open test-tenant
   alert through the existing admin test database URL so positive feed,
   acknowledgement, poll, and accessibility behavior traverse the real BFF and
   backend. Extend the existing BFF security contract test only for new
   UI-required behavior; avoid duplicating Phase 2 coverage. Route heavyweight
   browser verification to guarded CI while disk policy warns.
7. Update docs only after code/contracts are verified. Record exact run IDs,
   rollback method, host-independent compose handoff, and unresolved external
   VPS/release ownership.

## Test scenario matrix

| Priority | Scenario | Proof |
|---|---|---|
| Critical | No alert permission | Bell/panel cannot fetch a feed; direct BFF/backend still denies. |
| Critical | Client tenant/profile/query injection | Empty-query/body schema rejects it before upstream. |
| Critical | Ack retry/unknown result | Exact `{}`, same-origin credentials, CSRF cookie/header, stable idempotency key, disabled in-flight action, safe replay state. |
| Critical | Positive real path | Fixture-backed authenticated persona fetches and acknowledges a valid open tenant alert through BFF and backend; cleanup removes only fixture data. |
| High | Panel keyboard lifecycle | Real-browser trigger/expanded state, focus, Escape/restore, screen-reader labels. |
| High | Poll lifecycle | Node lifecycle tests + browser proof: starts only while open/visible; cleanup/abort has no leaked timers or fetches. |
| High | Provenance and stale/partial state | Clearly differentiates realtime operational source from Gold alerts. |
| High | Responsive/a11y | 375/768/1024/1440 + landscape, axe, no horizontal overflow, reduced motion. |
| Medium | Upstream failure/timeout | Readable retry path, no raw error/token/correlation leakage. |

## Todo list

- [x] Implement strict generated-contract reuse and client-safe state boundary.
- [x] Replace header bell with accessible Field Ledger alert panel.
- [x] Add bounded polling/refresh/acknowledgement with cleanup.
- [x] Prove BFF, component, responsive/a11y, and real-persona browser flows.
- [x] Synchronize architecture/contract/deployment/roadmap evidence and report.

## Success criteria

- [x] Panel renders only authorized, typed, source-labelled data through a
  same-origin BFF route; no browser token or direct upstream call exists.
- [x] Keyboard, focus, motion, responsive, error, stale, partial, and empty
  states satisfy the Field Ledger and WCAG requirements.
- [x] Polling and acknowledgement are bounded/replay-safe and do not alter
  worker-owned alert state client-side.
- [x] Heavy integration/browser evidence passes in guarded CI; local disk guard
  is respected and no C/D threshold is weakened.
- [x] Docs/report state exactly what was proven and list a real VPS/protected
  release as an owner-gated next action, not a completed deployment.

## Risk assessment

- An app-header client component could inflate every route. Mitigation: lazy
  load the panel on first open, keep initial bundle tiny, and preserve server
  app shell by default.
- A notification panel can obscure mobile content. Mitigation: safe responsive
  inset/maximum height/focus behavior and tested landscape layout.
- Aggressive polling can create invisible load. Mitigation: open-only interval,
  visibility/abort cleanup, one request in flight, and manual refresh fallback.

## Security considerations

- Permission-gated visibility is UX only; the BFF and backend enforce every
  read/mutation again.
- Browser acknowledgement uses only the existing CSRF double-submit transport
  and same-origin credentials; it never imports server auth/session helpers or
  accesses a bearer token.
- Render response text as text, not HTML. Never expose raw error/outbox payload
  or cross-profile acknowledgement data.
- Preserve CSP `connect-src 'self'`; no external stream, image, analytics, or
  provider is added by this feature.
- The E2E fixture is test-only and may use only the existing guarded admin test
  database URL. It creates deterministic IDs, limits cleanup to those IDs, and
  is never imported by application runtime code.

## Next steps

After all acceptance evidence is recorded, domain-specific agriculture alerts
may be planned separately around a versioned semantic-event contract or
source-side policies. External deployment needs real host credentials and a
protected release owner; ChatGPT/Codex cannot supply a VPS.
