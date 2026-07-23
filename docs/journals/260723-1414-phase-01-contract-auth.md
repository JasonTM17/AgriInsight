---
title: Phase 1 contract/auth gate closed after blocker review
date: 2026-07-23 14:14
severity: High
component: Backend contract + session/auth spike
status: Resolved
---

# Phase 1 contract/auth gate closed after blocker review

**Date**: 2026-07-23 14:14  
**Severity**: High  
**Component**: Backend contract + session/auth spike  
**Status**: Resolved

## What Happened

Phase 1 did not get to call itself done on the first pass. The happy-path backend gates looked green, but the independent review surfaced seven production blockers: self-asserted Better Auth rejection, missing real SQL/RLS coverage for the new reads, orphaned OpenAPI headers, weak end-session/nonce proof, a too-short refresh wait loop, a logout-vs-refresh token race, and issuer drift not being enforced. After that, the auth spike had to be proven against the real Better Auth 1.6.24 package, not just the contract matrix.

The exact-package harness was brutal and useful: concurrent refresh consumers produced two provider calls, so Better Auth could not prove one-provider-refresh fencing. That was the point where `openid-client` 6.8.4 stayed and Better Auth got rejected for the production path.

## The Brutal Truth

This was frustrating because the first evidence set looked polished enough to fool a shallow review. It was not enough. The spike only became honest when it failed in front of the real package, the real Keycloak issuer, and the real browser. That hurt, but it also removed a bad assumption before it could become production debt.

## Technical Details

- Better Auth 1.6.24 exact-package refresh-race: `2` consumers -> `2` provider calls.
- Refresh waiter was extended from `1.5s` to a real bounded retry window.
- Real signed nonce mismatch returned `400`; real Keycloak end-session carried the logout redirect.
- Logout and refresh are covered in both race orders: logout-first revokes the discarded rotated token; refresh-first atomically returns and revokes the final rotated token.
- Persisted issuer drift now fails closed.
- Backend verification landed at `459` Surefire + `100` Failsafe passes.
- Auth verification landed at `16/16` unit, `7/7` PostgreSQL integration, and `1/1` real Chrome E2E.
- Disk stayed inside guardrails after D-local cache/runtime cleanup; last known free space was C `13.35 GB` and D `25.89 GB`.

## What We Tried

- Proved Better Auth through the contract matrix first.
- Then ran the exact 1.6.24 executable harness.
- Tightened the wait loop, added end-session proof, added logout/refresh race coverage, and enforced issuer drift.
- Rejected the tempting shortcut of “ship the spike and assume the rest will hold.”

## Root Cause Analysis

The root cause was over-trusting API shape and happy-path output. The first review was correct to block the phase because the implementation had not proved the hard cases. Better Auth was also the wrong fit for the session fence we needed: it could satisfy surface capabilities, but it did not give a transactional hook to guarantee one provider refresh against an expected session version.

## Lessons Learned

- If the package cannot prove the race, do not pretend the architecture is safe.
- A 1.5s polling window is not evidence; it is wishful timing.
- Logout, refresh, nonce, issuer, and end-session all need real-path proof, not contract-only proof.
- `openid-client` won because it let us own the session lifecycle cleanly.

## Next Steps

- Phase 2: keep extending the real backend reads and warehouse-facing contracts, with the same bounded-collection discipline.
- Phase 3: lock the production issuer/MFA contract, secret storage, key rotation, and deployment origin/cookie policy before any release claim.
- Keep the later protected release gate separate from this phase closure; Phase 1 is closed, production is not.

## Unresolved Questions

- Which production issuer and step-up rule replaces the disposable Keycloak setup?
- Which secret manager owns provider-token ciphertext and key rotation?
- Should production prove remote revocation state, or is best-effort remote revoke enough when local revoke succeeds?
