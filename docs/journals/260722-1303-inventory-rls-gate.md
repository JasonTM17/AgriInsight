---
title: Inventory RLS Gate
date: 2026-07-22 13:03
severity: High
component: backend inventory migrations and RLS catalog
status: Ongoing
---

# Inventory RLS Gate

## What Happened

The warehouse vertical slice landed 8 tenant tables in `V12__create_inventory_tables.sql` and 16 intended permissive policies in `V13__add_inventory_rls_policies.sql`. Full verify surfaced a stale catalog assertion: the test still expected 36 permissive policies, but the live catalog had 52. The actual fix is blunt: assert 52, not 36.

## The Brutal Truth

This was not a subtle bug. We had the right database shape and the wrong expectation baked into the gate. That is wasted time, and it is exactly the kind of drift that makes late-stage verification feel like punishment.

## Technical Details

- `TenantRlsIntegrationTest.policyCatalogKeepsForceRlsAndRoleSpecificPermissivePolicies()`
- Before the fix: 146 Surefire suites green, 76/77 integration green
- Focused method: 1/1 pass
- RLS blast-radius check: 16/16 pass
- Stale assertion: expected 36 permissive policies
- Live catalog: 52 permissive policies
- Exact correction: `pg_policies` count assertion updated to 52

## What We Tried

- Verified the new inventory migrations instead of guessing from the test failure
- Checked the RLS policy catalog directly rather than papering over the mismatch
- Kept the fix narrow: catalog assertion only, no schema churn, no policy rewrite
- Paused when disk pressure hit and the workspace C: volume dropped below 8 GB
- Moved Codex data to D: through a junction, removed regenerable caches, and avoided any broad Docker prune

## Root Cause Analysis

The root cause was stale test expectation, not broken RLS. The inventory slice expanded the policy catalog, but the integration test still encoded the old count. That is a maintenance failure, plain and simple.

## Lessons Learned

Policy-count assertions need to track schema growth or they rot fast. If a migration adds tenant tables and policies, the catalog check has to be updated in the same change set. Also: disk guard warnings are real; ignoring them just buys a bigger failure later.

## Next Steps

Re-run the full verify only at the next coherent Phase 5 checkpoint. Do not trigger it early; the Docker VHD expansion risk is not worth it. Ownership stays with the backend inventory gate until the next checkpoint validates the 52-policy catalog cleanly.
