# Cost Analysis reconciliation desk milestone

**Date**: 2026-07-19 20:48
**Severity**: Medium
**Component**: Cost Analysis design prototype
**Status**: Resolved

## What Happened

We finished the Gold-backed Cost Analysis read-only prototype and locked the design around a reconciliation desk with two separate lenses: Operating P&L and Procurement spend non-P&L. Inventory value stayed separate and was not folded into either cost lens. The prototype passed static checks, browser checks, print checks, and independent review after several fixes.

The work surfaced the same kind of mistakes that usually make dashboard work rot later: schema boundaries were too loose, responsive focus handling could drift across breakpoints, URL state could keep garbage parameters, and the print/live-region behavior leaked UI details that should not be visible.

## The Brutal Truth

This was not hard because the math was hard. It was hard because the UI could have lied in several small ways at once: wrong defaults, hidden focus, malformed dates, and navigation state that looked valid until a user or reviewer pushed on it. That is exactly how a finance view turns into a trust problem.

The good part is that we caught those failures before shipping any production frontend. The annoying part is that the prototype would have looked polished while still being wrong in edge cases.

## Technical Details

- Verified source metrics:
  - Operating total: `266,980,248,000 VND`
  - Procurement spend: `41,631,673,900 VND`
  - Reconciled records: `48/48` with zero delta
  - Browser rows stayed bounded: Operating `19` monthly rows, `7` drivers, `6` comparisons; Procurement `7` trends, `8` suppliers, `15` materials
- Static gate finished at `44/44` passing, `0` failing.
- Browser gate covered `375`, `768`, `1024`, `1440`, and `844 x 390` landscape, plus print and `200%` zoom.
- Safe disk recovery was verified: C drive was recovered to `12.918 GB` and D stayed at `38.352 GB`; Docker Desktop remained stopped during the work.

The bugs that mattered:

1. Fixture validation was too permissive until we tightened it to reject blank labels, impossible dates/timestamps, duplicate IDs, invalid defaults, and oversize collections.
2. Mobile rail and breakpoint logic needed inert/aria-hidden parity and correct focus transfer when switching between desktop and mobile.
3. URL state had to be canonicalized so malformed or unknown params do not survive in history.
4. The live-region status text needed to remain announced without being visually exposed.
5. Print output had to hide skip link, nav scrim, and navigation chrome so the PDF is usable.

## Decisions and rejected alternatives

- A single blended finance view was evaluated and rejected: it would mix operating cost and procurement spend, creating a double-counting trap.
- Browser-side recalculation of canonical finance values was evaluated and rejected: the UI should render and validate precomputed Gold data, not become a second accounting engine.
- An active file-generating export was evaluated and rejected: the backend export contract is not accepted, so the prototype keeps export as a disabled preflight only.

## Root Cause Analysis

The root issue was scope drift pressure. Once a finance dashboard gets dense, it is easy to smuggle in assumptions: combined lenses, silent defaults, unbounded collections, and state that is only correct in the happy path. None of those are acceptable in a reconciliation view.

The prototype stayed honest only after we forced each boundary to be explicit: operating versus procurement, read-only versus write path, visible versus announced state, and valid versus canonical URL state.

## Lessons Learned

- Finance UI must make double-counting impossible by design, not by user discipline.
- Read-only prototypes still need real schema validation. Fake flexibility becomes fake trust.
- Responsive focus handling is not a cosmetic issue; if focus can disappear into hidden chrome, keyboard users lose the workflow.
- A disabled export contract is better than a fake export button that implies a backend capability we do not have.
- Print and accessibility need their own evidence. A screen-only approval is not enough.

## Next Steps

- Keep the Cost prototype read-only until backend Phase 6 owns the operational ledger and export contract.
- Keep Inventory value as a separate journey and do not merge it into either Cost lens.
- Leave Docker stopped until the next backend/image gate needs it again.
- Continue the next milestone with the remaining browser gate work, then resume backend acceptance only after the integration environment is healthy.

## Unresolved Questions

- Which operating-cost comparison should be canonical in production: approved budget, prior period, or both?
- Which roles may see supplier identity, unit price, and procurement detail across warehouses?
- What reconciliation delta should block export versus mark it provisional?
- Which backend endpoint will own the signed export and retention policy?
