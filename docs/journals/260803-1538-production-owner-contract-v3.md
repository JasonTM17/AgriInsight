---
title: Production owner contract v3 still does not produce GO
date: 2026-08-03 15:38
severity: High
component: external production readiness / promotion evidence
status: Blocked
---

# Production Owner Contract v3

## Context

We were tightening the external production-readiness contract for AgriInsight. The repo can validate its own promotion evidence, but it cannot invent external owners, approvals, hosting rights, broker ownership, or a production GO. That boundary stayed real, and it should have been obvious from the start.

## What happened

The first pass used a broad placeholder-expansion approach and it bled into unrelated contracts. That was the wrong shape. Review caught three hard failures: coercive `format_version` handling, Unicode confusable bypasses in unresolved-owner markers, and acceptance of extra row fields that did not belong in the approval schema.

We fixed it with a stricter v3 contract and the focused test suite went red, then green. The final focused run finished at 47 passing tests. Broad gates were not run because the workstation disk guard blocked them: C had 8.046 GiB free and D had 16.949 GiB free. That was enough to stop pretending this was a full release validation.

## Failure chain / root causes

1. I treated placeholder normalization as a reusable convenience instead of a contract boundary.
2. That widened rejection scope and changed unrelated records instead of only hardening the approval rows.
3. Review exposed the exact holes: `format_version` had to be exact, owner placeholders had to fail under Unicode normalization, and the approval rows had to be closed over a fixed field set.
4. We never had external owner approval in hand, so the repo work could only preserve a truthful NO-GO, not turn it into GO.

## Decisions and rejected alternatives

We chose a strict v3 schema with exact `format_version: 3`, required `rollback_responsibility`, and rejection of unknown approval fields. I rejected the softer option of keeping v2 compatibility and “helpful” placeholder coercion, because that would keep the hole open and let bad evidence look valid.

I also rejected any attempt to mark production ready without real owner input. That would have been fake progress.

## Verification

- TDD red/green completed.
- Final focused suite: 47 passed.
- Review findings resolved: coercive `format_version`, Unicode confusable bypasses, extra row fields.
- Broad validation gates not executed due to disk limits on C/D.
- No external owner approval, no production GO, no push.

## Next actions

1. Restore workstation capacity enough to run the broader gates.
2. Collect real owner approvals and timestamps from the responsible org.
3. Re-run the production readiness checks against the v3 contract.
4. Do not claim hosted, external, or production validation until those gates actually pass.

## Unresolved questions

- Which external owner is accountable for each control row?
- When are the approval references due?
- Which environment is authorized for the next production gate?
