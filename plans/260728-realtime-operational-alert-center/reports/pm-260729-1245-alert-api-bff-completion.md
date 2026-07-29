# Phase 2 completion: alert API and BFF

Date: 2026-07-29
Status: completed, pending PR merge
PR: [#13](https://github.com/JasonTM17/AgriInsight/pull/13)
Implementation SHA verified by CI: `d781fe49419f2b8ae0508897cc958a1c8cf70124`
Hosted evidence: CI run `30425647823`

## Delivered

| Boundary | Verified result |
|---|---|
| PostgreSQL | V29 rejects acknowledgement of non-open rows under the existing lock; V30 adds the exact concurrent partial feed index. |
| Runtime read model | Permission-first tenant/profile scope, current-profile acknowledgement projection, exact severity/time/UUID ordering, 51-row lookahead for a 50-item response. |
| Spring API | Fixed no-query `GET /api/v1/realtime/alerts`; exact-empty-body idempotent `POST /api/v1/realtime/alerts/{id}/acknowledgements`; sanitized 403/404 behavior. |
| Public contract | Closed request schema; required/nullable/enumerated response fields; deterministic OpenAPI and generated TypeScript. |
| Next BFF | Literal operation allowlist, trusted host/origin/CSRF/session/idempotency order, dedicated permissions, bounded bodies/responses, caller cancellation, no token/upstream-body leakage. |

## Evidence

| Gate | Result |
|---|---|
| Local backend focused suite | 18/18 pass |
| Local alert service + HTTP regression suite | 14/14 pass after wiring fix |
| Local BFF/contract suite | 68/68 pass |
| Generated contract tests | 2/2 pass |
| Local web typecheck, lint, contract drift, production build | pass |
| OpenAPI export | 1/1 pass; checked-in SHA-256 matched |
| CI dependency/config/secret scan | pass, 16s |
| CI Python analytics | pass, 1m17s |
| CI Next web foundation | pass, 55s |
| CI Java backend | pass, 4m22s |
| CI real PostgreSQL/Kafka outbox | pass, 1m41s |
| CI seven-persona browser | pass, 7m7s |
| CI image builds | analytics-api, backend, python, web all pass |

## CI defects closed

- PostgreSQL 18 renders `pg_get_indexdef` predicates with explicit text casts.
  The upgrade proof now asserts stable semantic tokens while the exact V30
  source assertion and planner-selection proof remain intact.
- `RealtimeOperationalAlertService` has production and clock-injected
  constructors. The production constructor is now explicitly selected for
  Spring wiring, matching the established multi-constructor service pattern.

## Remaining

- Phase 3: keyboard-accessible header alert panel, states, low-frequency
  open-only polling, responsive acceptance, screenshots/GIF, and docs.
- Full project goal still includes later ML, domain alert policies, guarded
  Text-to-SQL, and a real external deployment target. The scoped DeepSeek RAG
  assistant is already implemented and included in released images.

## Unresolved questions

- None for Phase 2 acceptance.
