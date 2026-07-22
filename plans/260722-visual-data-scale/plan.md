---
title: Production-like data scale and visual evidence
status: completed
created: 2026-07-22
updated: 2026-07-22
---

# Production-like data scale and visual evidence

## Outcome

Make the current analytics MVP feel credible in an end-to-end demonstration:
one named big-data profile generates at least one million sensor readings, and
every dashboard area has an intentional first-party visual with explicit demo
provenance. This plan does not turn generated crop-health imagery into factual
field evidence or bypass the future authenticated React application.

## Phases

| Phase | Status | Deliverable |
|---|---|---|
| [01](./phase-01-big-data-profile.md) | Completed | Reproducible `big-data` CLI profile, guarded runner, manifest evidence |
| [02](./phase-02-generated-visual-assets.md) | Completed | Optimized image set, provenance catalog, social preview source |
| [03](./phase-03-dashboard-integration-and-gates.md) | Completed | Page integration, visual/data tests, documentation, browser review |

## Dependencies

- Existing deterministic Bronze-Silver-Gold pipeline and disk guard.
- Existing Streamlit dashboard remains the local/internal UI.
- Existing Field Ledger design system remains the future web source of truth.
- Heavy artifacts stay below `artifacts/` on drive D and remain untracked.

## Acceptance criteria

- `python -m agriinsight run --profile big-data` resolves to a documented,
  deterministic configuration producing at least 1,000,000 raw sensor rows.
- The guarded big-data runner checks both C and D before and after execution.
- A completed big-data run passes quality, warehouse foreign-key, checksum, and
  dashboard Gold-contract gates without changing the standard profile.
- Six generated WebP assets are committed at practical runtime sizes; the
  source, intended page, alt description, and demo-evidence disclaimer are
  documented.
- Executive, Farm Performance, Inventory, Crop Health, Data Quality, and Cost
  Analysis each render the intended visual without a missing-file failure.
- The crop-health image is visibly identified as generated demonstration
  evidence and is never linked to a real observation identifier.
- Focused tests, full Python regression, compileall, docs validation, and disk
  checks pass; changes are committed in small conventional groups.

## Release boundary

The image files are repository/application assets. Docker image publication,
SBOM/provenance, registry signatures, and immutable tags remain Phase 7 work.

## Verified evidence

Full evidence: [`reports/acceptance-2026-07-22-visual-data-scale.md`](./reports/acceptance-2026-07-22-visual-data-scale.md).

- Big-data runner: 1,050,003 Bronze sensor rows, 1,050,000 Silver/warehouse
  sensor facts, quality `passed`, 74 checksums, 388.2 MB artifacts.
- Python regression: 75 passed, 3 intentional optional-PDF skips; compileall
  passed; focused visual/dashboard gate 13/13.
- Browser gate: live big-data Streamlit Executive and Crop Health routes
  rendered visuals; Crop Health warning displayed as specified.
- Disk guard: latest post-gate C 10.274 GB and D 25.364 GB, both PASS.

## Deferred account/release action

The GitHub social-preview source is committed, but the authenticated browser
file chooser rejected upload in this environment. A repository owner can upload
`docs/assets/agriinsight-social-preview.jpg` once in GitHub Settings; no token
or cookie was copied into the repository.
