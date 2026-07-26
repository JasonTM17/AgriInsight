# Phase 7 landing review — inventory control

Date: 2026-07-27
Verdict: **LAND**

## Scope

- Phase 7 remediation commits from `071a28d` through `8cc709a`
- Current PostgreSQL UTF-8 bootstrap remediation
- Inventory BFF, mutation hook/forms, contract tests, browser E2E, CSP rendering,
  E2E Compose health, lifecycle tests, and acceptance documentation

## Blocking findings

None. The review found **0 Critical** and **0 High** defects.

## Verified invariants

- An ambiguous reversal retry preserves the original `Idempotency-Key` and
  `If-Match`; the real-platform test proves one reversal and exactly three
  inventory ledger rows overall.
- Spring authorizes before claim, claims/replays idempotency before mutation,
  then checks the source version inside the mutation.
- ABC shares use native `<progress>` values and emit no inline `style` attribute.
- E2E PostgreSQL readiness uses an authenticated TCP `SELECT 1`.
- Demo bootstrap pins `PGCLIENTENCODING=UTF8` and restores the caller's prior
  value in `finally`.
- Mutation routes retain strict Host, origin, CSRF, session, body-size, media
  type, JSON, schema, idempotency, and `If-Match` boundaries.

## Verification

- `npm --prefix web run lint` — pass, zero warnings
- `npm --prefix web run typecheck` — pass
- `npm --prefix web run test` — 211 pass / 9 intentional skip
- merged Compose configuration — pass
- Controller runtime evidence — 8/8 Playwright and
  `WEB_PLATFORM_E2E=PASS`

## Informational follow-up

The review found a Phase 7 acceptance-date mismatch between README/phase table
and the final validation log. It was normalized to 2026-07-27 before landing.

## Unresolved questions

None for local Phase 7 acceptance. Public release remains governed by the
separate Phase 11/12 release gates.
