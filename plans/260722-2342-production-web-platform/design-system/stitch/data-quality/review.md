# Data Quality Stitch evidence

Status: accepted as composition evidence; rejected as production code.

## Source

- Stitch project: `9084754434575632570` (`AgriInsight/backend-auth-rbac`)
- Field Ledger design system: `assets/c1989dfbbef24da0a3d2617a620edb8a`, version `1`
- Screen: `f53a916e084e4c398e41095407e51ad3`
- Title: `Chất lượng dữ liệu - AgriInsight`
- Canvas: `2560 × 2048`
- Evidence image: `design.png`
- Evidence SHA-256: `e7bef40b6d61385558b10a60d3ab9905a5924ace4d6857ecbd7e912f0f4716e2`
- Generation session: `13433946603941843226`
- Review edit session: `13433946603941843226`

## Accepted direction

- The Bronze → Quarantine → Silver lineage is the primary visual anchor.
- Freshness, completeness, validity, and uniqueness are shown with text and
  icons rather than colour alone.
- The anomaly ledger provides the right operational shape for source, owner,
  reason, and recovery action.
- The page has a single safe reconciliation path and a compact coverage matrix.

## Implementation blockers

- The generated heading wraps as `Chất lượng dữ liệu hệ thống`; production
  should keep the title concise and reserve a separate subtitle for the run
  context.
- All run IDs, fingerprints, timestamps, counts, owners, and recovery actions
  must come from the API/analytics contracts; the illustration is not a KPI
  fixture.
- Add an explicit failed-source recovery state and keep data quality distinct
  from business KPI meaning.

## Handoff rules

Rebuild with semantic components, accessible table alternatives, bounded
large-dataset rendering, and no raw Stitch HTML or public CDN dependency.
