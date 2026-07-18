# Controlled Report Export Phase 2

**Date**: 2026-07-18 22:07
**Severity**: High
**Component**: cost analysis report export / packaging / XLSX fallback
**Status**: Resolved

## What Happened

Phase 2 looked “done” on the first pass because the service was polished and tests passed, but review forced the real truth out: we were still shipping a source-only illusion. Four blockers were real: CSV had no run lineage, the wheel/Docker image dropped required fonts and builder assets, row caps happened too late after eager copy/sort, and raw XLSX filesystem errors could still punch through the optional fallback path.

We fixed the damage and re-ran the gates. The final bundle is real: CSV `43,090 bytes / 120 rows`, PDF `31,524 bytes / 3 pages`, XLSX `28,021 bytes` with `QA PASS`, and the full suite ended `30 passed, 2 expected skips`. The wheel smoke outside the repo passed too. That is the first time this export path felt trustworthy instead of merely optimistic.

## The Brutal Truth

This was frustrating as hell because the first version was neat-looking but wrong in ways that matter. A pretty export that loses provenance or dies on packaging is not a release; it is paperwork with good formatting. The repeated review/QA loops were exhausting, but they prevented us from shipping misleading audit metadata and a brittle fallback that would fail under real filesystem pressure.

## Technical Details

- CSV now carries five lineage fields, including verified manifest `source_pipeline`; the fabricated `generated_at` timestamp is gone.
- Assets are packaged inside the wheel, so the builder/fonts resolve from install context instead of repository CWD.
- The row cap now applies before expensive sort/copy work, using one-mask filtering for individual and combined caps.
- All XLSX filesystem failures are normalized, and temp cleanup is preserved.
- CI now covers wheel smoke and Node syntax checks.
- Vietnamese PDF translation initially let the `MODEL STATUS` table spill alone onto page 4; compact semantic labels restored deterministic 3-page output.
- Disk evidence stayed sane: D held about `29.27 GB`; C ended around `11.94 GB`. Earlier C pressure lined up with external Office activity, not the project adapter. QA temp was kept on D.

## What We Tried

- Kept the first pass and accepted the review findings. Rejected the shortcut of “good enough because tests pass.”
- Considered leaving timestamp-style lineage in place. Rejected it because it creates fake provenance.
- Considered looser XLSX failure handling. Rejected it because optional fallback must fail closed and stay observable.
- Considered allowing the translated PDF layout to float naturally. Rejected it because page breaks became nondeterministic.

## Root Cause Analysis

We shipped too close to the happy path. The core mistake was trusting source-level behavior and unit tests without verifying packaged runtime behavior, filesystem failure modes, and PDF pagination under translated content. The implementation was technically competent but operationally naïve.

## Lessons Learned

Trust the bundle, not the repo. Verify the installed artifact, not just local imports. Keep provenance fields real, not inferred. Apply caps before expensive transforms. Normalize filesystem failure at the boundary. And keep the three financial lenses separate: source, export, dashboard.

## Next Steps

Phase 2 is complete. Phase 3 owns dashboard wiring plus visual and disk verification, with controller ownership on that work. No auth/RBAC expansion is planned for that phase. Timeline: start Phase 3 from the current approved plan, then verify the dashboard path against the real bundled exports.

## Unresolved Questions

- None from Phase 2 itself.
