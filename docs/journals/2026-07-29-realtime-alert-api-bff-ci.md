# Realtime Alert API/BFF CI Fixes

**Date**: 2026-07-29 12:45
**Severity**: Medium
**Component**: Realtime Alert API, Next BFF, CI pipeline
**Status**: Resolved

## What Happened

Phase 2 of the realtime alert center was delivered, but the release path was noisy. The first CI run (`30424720107`) failed on a PostgreSQL 18 migration assertion around `pg_get_indexdef` output. The second run (`30425116841`) then exposed ambiguous Spring constructor wiring in the real outbox/Kafka gate. The final run (`30425647823`) passed and the alert feed/acknowledgement contract was accepted.

## The Brutal Truth

This was annoying because the code was mostly correct and the failures were in exactness, not business logic. PostgreSQL 18 changed how index definitions are rendered, and Spring wiring failed until the production constructor was made unambiguous. The work was not hard because the domain was complex; it was hard because small contract mismatches kept blocking the gate.

## Technical Details

- CI `30424720107`: migration test asserted a raw `pg_get_indexdef` string and tripped on PostgreSQL 18’s explicit cast rendering.
- Fix: loosen the assertion to compare semantic index tokens, not the exact casted string.
- CI `30425116841`: Spring failed to resolve the intended `RealtimeOperationalAlertService` constructor.
- Fix: mark the production constructor with `@Autowired` so Spring stops guessing.
- CI `30425647823`: green end-to-end acceptance for the Phase 2 alert API/BFF contract.
- Local evidence before merge: focused backend suite, web contract checks, typecheck, lint, and OpenAPI generation all passed.

## What We Tried

1. Kept the exact-string migration assertion. Rejected because it was brittle against PostgreSQL 18 formatting.
2. Swapped to a fake or relaxed path. Rejected because it would have hidden real index regressions.
3. Let Spring pick a constructor implicitly. Rejected because the runtime picked the wrong one under the production wiring path.

## Root Cause Analysis

The root cause was overfitting tests to representation instead of behavior. The migration test cared about the rendered SQL text, not the index semantics. The Spring failure came from ambiguous constructor selection in a class with more than one valid path. Both are classic “works locally until the exact runtime/container behavior shows up” problems.

## Lessons Learned

- Assert semantics, not exact renderer output, when the database vendor can legally change formatting.
- If a service has multiple constructors, wire the production one explicitly instead of trusting inference.
- Treat CI failures on exact contracts as real work, not noise. These are the bugs that survive until release day.

## Next Steps

Phase 2 is done. Phase 3 now owns the browser alert UX, states, and acceptance evidence. Next owner should build the panel against the finished API contract and keep the same strict boundary: no browser-side tenant scope, no proxy behavior, no contract drift.
