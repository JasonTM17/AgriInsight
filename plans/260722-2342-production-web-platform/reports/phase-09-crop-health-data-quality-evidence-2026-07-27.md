# Phase 9 Crop Health and Data Quality Evidence

Date: 2026-07-27  
Status: implementation complete; guarded browser/big-data gate pending disk recovery

## Scope

- Phase 2 analytics contract amendment: optional `field_code` scope for Crop
  Health detail and `assessmentMethod=rule-based-heuristic` on Data Quality.
- Strict server-only view-model loaders for Crop Health and Data Quality.
- Vietnamese-first routes: `/crop-health`, `/crop-health/[fieldCode]`, and
  `/data-quality`.
- Current-identity permission/role gating and direct navigation links.
- Lineage/taxonomy display, evidence tables, explicit degraded states, and
  permanent AI-generated demo-image warning.

## Evidence

| Gate | Result |
|---|---|
| Web full suite | 261 passed, 9 intentional skips |
| Focused Crop/Data Quality components and navigation | 8 passed |
| Phase 2 analytics endpoint/OpenAPI tests | 17 passed |
| Contract drift | PASS |
| Typecheck | PASS |
| Zero-warning lint | PASS |
| Next production build | PASS; 8 reviewed visuals synced |
| GitHub CI `30233453422` | PASS on `d5b9a9d`; Python, Java, web, security, and image-build jobs green |
| Guarded real browser | NOT RUN: disk guard stopped before startup |
| Big-data smoke | NOT RUN: same disk guard prerequisite |

## Disk observation

The default `scripts/check-workspace-disk.ps1` policy was run without an
override:

- C: 2.798 GiB free; status FAIL (fail below 8 GiB).
- D: 20.711 GiB free; status WARN (fail below 20 GiB).

The browser runner was not bypassed and no threshold was lowered. Docker build
cache was reclaimed; project containers, volumes, and images were preserved.

## Review notes

- Browser receives no analytics token or artifact path; reads stay behind the
  Next server loader/BFF allowlist.
- The strict runtime schema keeps `dataStatus`, `assessmentMethod`, `severity`,
  `runId`, `asOf`, and `generatedAt` sourced from the upstream envelope.
- Crop Health demo visuals are captioned with the permanent warning on both list
  and detail routes and are not used as measured field data.
- The phase must not be marked accepted until the same guarded browser and
  big-data gates pass after C/D recovery.

## Unresolved questions

- When can C: be recovered above the 8 GiB fail floor without changing the
  operator's intended Windows paging/hibernation policy?
