---
phase: 3
title: "Live operations UX and acceptance"
status: pending
effort: "1.5-2d"
---

# Phase 3: Live operations UX and acceptance

## Overview

Priority: P1  
Current status: pending; depends on Phases 1–2  
Owner boundary: Next UI, browser verification, docs, hosted technical evidence

Replace the inert app-header bell with a focused, Field Ledger alert panel. It
renders only the typed BFF contract, retains source and freshness provenance,
and supports a guarded user acknowledgement without pretending that an
operational alert is a batch Gold insight or a live WebSocket stream.

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
   run an always-on browser transport.
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
| `D:\AgriInsight\web\src\components\app-shell\app-shell.tsx` | Modify if needed | Pass only current server authorization/data needed by header. |
| `D:\AgriInsight\web\src\features\realtime-alerts\realtime-alert-contract.ts` | Create | Strict runtime/client view-model types; no general backend shape leakage. |
| `D:\AgriInsight\web\src\features\realtime-alerts\load-realtime-alerts.ts` | Create | Server/BFF-facing loader that owns retry/freshness/degraded mapping. |
| `D:\AgriInsight\web\src\features\realtime-alerts\components\realtime-alert-panel.tsx` | Create | Keyboard-accessible panel and fetch lifecycle. |
| `D:\AgriInsight\web\src\features\realtime-alerts\components\realtime-alert-row.tsx` | Create | Source-labelled evidence row/ack action. |
| `D:\AgriInsight\web\src\features\realtime-alerts\components\realtime-alert-panel.module.css` | Create | Field Ledger panel responsiveness/reduced-motion/focus styles. |
| `D:\AgriInsight\web\src\app\api\realtime\alerts\route.ts` | Modify if needed | Preserve BFF response headers/correlation/degraded state contract. |
| `D:\AgriInsight\web\src\app\api\realtime\alerts\[alertId]\acknowledgements\route.ts` | Modify if needed | Preserve safe acknowledgement response/cancellation handling. |
| `D:\AgriInsight\web\tests\features\realtime-alert-panel.test.tsx` | Create | Component states, source labels, focus, refresh/poll cleanup, and ack UI. |
| `D:\AgriInsight\web\tests\bff\realtime-alerts-route.test.ts` | Create | Session/origin/CSRF/query/response/correlation BFF behavior. |
| `D:\AgriInsight\web\tests\e2e\realtime-alerts.spec.ts` | Create | Real persona, responsive, a11y, token/CSP, and accessibility browser proof. |
| `D:\AgriInsight\docs\system-architecture.md` | Modify | Alert source, worker/API/BFF boundaries, and strict deferrals. |
| `D:\AgriInsight\docs\data-contracts.md` | Modify | Alert feed/ack contract, provenance, pagination, and no-payload rule. |
| `D:\AgriInsight\docs\backend-development.md` | Modify | V22 grants/permissions/worker lifecycle and verification commands. |
| `D:\AgriInsight\docs\deployment-guide.md` | Modify | Worker config/rollback and real-host-only deployment handoff. |
| `D:\AgriInsight\docs\project-roadmap.md` | Modify | Accepted scope/evidence or remaining owner gates. |
| `D:\AgriInsight\docs\codebase-summary.md` | Modify | Verified implementation snapshot after acceptance only. |
| `D:\AgriInsight\plans\260728-realtime-operational-alert-center\reports\acceptance-YYYY-MM-DD-realtime-alert-center.md` | Create | Actual hosted/local evidence, rollback, open owner inputs. |

## Implementation steps

1. Before coding, read the Field Ledger master and page-loading/BFF patterns.
   Record the exact panel state model: initial/loading/ready/stale/partial/
   denied/failed/acknowledging. A configured Stitch exploration is optional;
   never make generated design output the source of truth.
2. Build the strict view model and loader against Phase 2's generated contract.
   Reject unknown source/policy/severity/state fields rather than rendering a
   permissive object. Keep date/number formatting locale-aware and source
   labels explicit.
3. Convert the header bell into a permission-aware client boundary with an
   accessible semantic panel. Keep app-shell structural layout stable and use
   the existing icon family/tokens.
4. Implement request lifecycle: fetch on open, manual retry/refresh, bounded
   open-panel poll, abort on close/unmount, and acknowledgement request with a
   caller-stable idempotency key. Do not optimistic-resolve the source alert.
5. Add robust visual states and screen-reader announcements. Test keyboard
   escape/focus restoration, reduced motion, high zoom/text sizing, narrow
   viewport, stale timestamp, partial feed, source distinction, and denied
   permission.
6. Run focused web tests, typecheck, lint, build, and browser gate where
   capacity permits. Route heavyweight browser/Kafka/PostgreSQL verification to
   guarded CI while disk policy warns.
7. Update docs only after code/contracts are verified. Record exact run IDs,
   measured freshness/recovery, rollback method, host-independent compose
   handoff, and unresolved external VPS/release ownership.

## Test scenario matrix

| Priority | Scenario | Proof |
|---|---|---|
| Critical | No alert permission | Bell/panel cannot fetch a feed; direct BFF/backend still denies. |
| Critical | Client tenant/profile/query injection | Empty-query/body schema rejects it before upstream. |
| Critical | Ack retry/unknown result | Stable idempotency key, disabled in-flight action, safe replay state. |
| High | Panel keyboard lifecycle | Trigger/expanded state, focus, Escape/restore, screen-reader labels. |
| High | Poll lifecycle | Starts only while open; cleanup/abort has no leaked timers or fetches. |
| High | Provenance and stale/partial state | Clearly differentiates realtime operational source from Gold alerts. |
| High | Responsive/a11y | 375/768/1024/1440 + landscape, axe, no horizontal overflow, reduced motion. |
| Medium | Upstream failure/timeout | Readable retry path, no raw error/token/correlation leakage. |

## Todo list

- [ ] Implement strict generated-contract view model and server/client boundary.
- [ ] Replace header bell with accessible Field Ledger alert panel.
- [ ] Add bounded polling/refresh/acknowledgement with cleanup.
- [ ] Prove BFF, component, responsive/a11y, and real-persona browser flows.
- [ ] Synchronize architecture/contract/deployment/roadmap evidence and report.

## Success criteria

- [ ] Panel renders only authorized, typed, source-labelled data through a
  same-origin BFF route; no browser token or direct upstream call exists.
- [ ] Keyboard, focus, motion, responsive, error, stale, partial, and empty
  states satisfy the Field Ledger and WCAG requirements.
- [ ] Polling and acknowledgement are bounded/replay-safe and do not alter
  worker-owned alert state client-side.
- [ ] Heavy integration/browser evidence passes in guarded CI; local disk guard
  is respected and no C/D threshold is weakened.
- [ ] Docs/report state exactly what was proven and list a real VPS/protected
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
- Render response text as text, not HTML. Never expose raw error/outbox payload
  or cross-profile acknowledgement data.
- Preserve CSP `connect-src 'self'`; no external stream, image, analytics, or
  provider is added by this feature.

## Next steps

After all acceptance evidence is recorded, domain-specific agriculture alerts
may be planned separately around a versioned semantic-event contract or
source-side policies. External deployment needs real host credentials and a
protected release owner; ChatGPT/Codex cannot supply a VPS.
