# Docs Manager Report - 2026-07-18 - Controlled Report Export

## Current State Assessment

- README and the three phase docs now distinguish the reusable backend export service from the pending dashboard wiring.
- Export semantics are documented against the verified contract: allowlisted filters, separate operating/procurement/value lenses, bounded bundle size, and XLSX as an explicit optional capability.
- Markdown validation passed clean after removing false-positive symbol/config references.

## Changes Made

- Updated [README.md](../../../README.md) with `reports` install guidance and explicit XLSX runtime preconditions, while noting dashboard download wiring remains Phase 3.
- Rewrote [docs/architecture.md](../../../docs/architecture.md) to add the controlled cost-report export path and the backend service split from Streamlit.
- Rewrote [docs/data-contracts.md](../../../docs/data-contracts.md) to add the export contract, request allowlist, row/bundle caps, CSV/PDF/XLSX semantics, and lineage fields using `source_pipeline`.
- Rewrote [docs/mvp-acceptance.md](../../../docs/mvp-acceptance.md) to add a completed Phase 2 export-service evidence item and keep the Custom Report Builder/dashboard backlog item unchecked.

## Gaps Identified

- Dashboard buttons/download UX is still Phase 3 and should not be described as shipped.
- The project docs still do not publish a `.env.example` entry for the explicit XLSX runtime variables, so the README must carry the setup note.

## Recommendations

1. Keep Phase 3 documentation separate so the backend export service does not get conflated with dashboard wiring.
2. Mirror the explicit XLSX runtime preconditions in any future setup or deployment doc that covers report downloads.
3. Add a focused user-facing export guide only after dashboard wiring lands, so the contract and the UI stay synchronized.

## Metrics

- Files updated: 4 docs + 1 report
- Validation: `node .claude/scripts/validate-docs.cjs docs/` passed
- Validation warnings remaining: 0
- Documentation scope: backend export service documented; dashboard wiring still pending

## Unresolved Questions

- None.
