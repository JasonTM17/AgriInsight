# Phase 10 Static Landing Review

Date: 2026-07-27
Verdict: **STATIC LAND; BROWSER ACCEPTANCE BLOCKED**

## Scope

- Phase 10 commits `c9de563` through `35557d3`
- 47 implementation/test files and the Next configuration
- Read boundaries, mutation dispatch, permissions, concurrency, error mapping,
  identity-data exposure, UI states, and navigation

## Blocking Findings

No Critical or High implementation defect remains in static review.

The first review pass found one acceptance defect: denied admin pages rendered a
denied panel but did not prove an HTTP 403 status. Commit `1e6244b` replaced
that behavior with Next 16 `forbidden()`, enabled `authInterrupts`, and added an
admin-segment forbidden boundary. Runtime confirmation remains blocked.

## Verified Invariants

- All Spring calls originate from frozen allowlisted operation names.
- External inputs are strict Zod unions; unexpected keys, invalid UUIDs, unsafe
  role codes, oversized fields, and weak/missing ETags fail closed.
- Server authorization checks the fresh Spring identity, exact permission, and
  Supplier denial before dispatch.
- CSRF, origin, trusted host, opaque session, bounded JSON, idempotency, and
  optimistic concurrency are retained from the established mutation boundary.
- All 13 admin command variants map to their exact upstream operation.
- Concurrent mutation statuses remain distinct; upstream bodies and stack
  details do not cross the BFF.
- Read pages are bounded to 50 records; relation/catalog requests reject a
  truncated second page instead of silently presenting incomplete authority.
- OIDC issuer is reduced to a constant provider label and subject never enters
  the read model. Retry state retains only a SHA-256 fingerprint.
- Supplier is removed from navigation and denied again on server reads and
  mutations.

## Verification

- `npm --prefix web test` — 302 pass / 9 intentional skip
- focused dispatcher matrix — 13/13 pass
- `npm --prefix web run lint` — pass, zero warnings
- `npm --prefix web run typecheck` — pass
- `npm --prefix web run contracts:check` — pass
- GitHub CI `30236258854` — pass across Java, Next production build, Python,
  secret/config scan, and both no-push image builds

## Informational Follow-up

- Activity assignment controls currently accept verified UUIDs from the Work
  area because the locked admin read contract has no tenant-wide employee
  assignment search endpoint. This avoids an unbounded N+1 catalog.
- Component/browser behavior, actual HTTP 403, keyboard flow, screenshots and
  visual regression remain Phase 10/11 browser evidence, not static claims.

## Docs Impact

Minor: Phase 10 status and evidence updated. Architecture and public setup are
unchanged.

## Unresolved Questions

- Disk recovery timing for the guarded browser suite.
