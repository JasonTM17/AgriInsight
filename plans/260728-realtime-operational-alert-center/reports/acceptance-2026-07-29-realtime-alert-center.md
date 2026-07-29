# Realtime operational alert center — Phase 3 acceptance

## Status

Phase 3 is accepted and merged. The browser implementation at feature head
`e8a02a2` passed hosted CI
[`30445148252`](https://github.com/JasonTM17/AgriInsight/actions/runs/30445148252).
[PR #14](https://github.com/JasonTM17/AgriInsight/pull/14) was rebase-merged
on `main` at `bd724503dd3e0864cbd546a6398216fbcd053f31`.

## Evidence

- Hosted CI passed all 10 gates: Python analytics, dependency/configuration/
  secret scanning, Next web foundation, Java backend, seven-persona real-browser
  acceptance, real PostgreSQL/Kafka outbox verification, and four candidate
  image-build validations.
- Local `npm --prefix web run typecheck`, `npm --prefix web run lint`, and
  `npm --prefix web test` passed. Vitest reported 374 passed and 9 intentional
  skips.
- The local heavy E2E suite was deliberately not run. Its disk guard measured
  the D drive below the 20 GiB heavy-work floor, so the protected threshold was
  kept intact and hosted CI supplies the browser acceptance evidence.
- The validation run built candidate images without pushing them. It did not
  publish a new Docker Hub/GHCR image or approve external deployment.

## Scope proven

- The app-header Field Ledger panel is authorization-gated, lazy-loaded, and
  calls the typed same-origin BFF only.
- The panel has bounded open/visible polling, stale/partial/denied/failure
  states, abort and cleanup behavior, and server-confirmed acknowledgement with
  CSRF and stable idempotency handling.
- Real persona browser scenarios cover the fixture-backed alert feed and
  acknowledgement path, accessibility, CSP/token boundaries, and responsive
  behavior.

## Release and deployment boundary

`v0.2.3` remains the protected worker-only Docker Hub/GHCR release from Phase
1. Phase 3 has no newly published image, public host, production Kafka
ownership, recovery objective, or production OIDC approval. ChatGPT/Codex is
not a deployment target.

## Rollback

Disable the evaluator/consumer configuration before removing the UI/BFF entry.
The additive schema and immutable acknowledgement history remain intact; do not
rewrite V22 or delete alert history as a rollback shortcut.

## Owner-gated follow-up

- Approve production threshold and on-call ownership for transport policies.
- Supply a real protected host, production OIDC/broker operations, recovery
  objectives, and observability controls before any external deployment.
- Use the existing protected registry workflow only when a separately approved
  image promotion is required.

## Unresolved questions

- Production policy ownership, hosting, recovery objectives, and observability
  remain unresolved and intentionally out of this Phase 3 acceptance.
