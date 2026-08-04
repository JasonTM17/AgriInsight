---
phase: 2
title: Fix responsive UI contracts
status: completed
priority: P1
effort: 4h
dependencies:
  - 1
---

# Phase 2: Fix responsive UI contracts

## Overview

Add failing browser assertions first, then repair responsive layouts without
changing KPI values, authorization, data contracts, or semantic HTML.

## Requirements

- Full VND values remain exact and readable.
- Mobile metadata and tabs require no horizontal discovery to read labels.
- Existing keyboard, zoom, and screen-reader behavior remains intact.

## File Inventory

| Action | Path | Test impact |
|---|---|---|
| Modify | `web/tests/capture/portfolio-media.spec.ts` | Add containment/collision assertions |
| Modify | `web/src/features/overview/components/overview-farms.module.css` | Contain lead KPI |
| Modify | `web/src/features/costs/components/cost-analysis.module.css` | Keep currency KPI readable |
| Modify | `web/src/features/crop-quality/components/crop-quality.module.css` | Mobile contract layout |
| Modify | `web/src/features/admin/components/tenant-administration.module.css` | Mobile tabs |

## Tests Before

- Add bounding-box checks proving Overview lead value cannot intersect sibling metrics.
- Add one-line/containment checks for Cost KPI values.
- Add mobile layout assertions for contract metadata and Admin tabs.

## Test Scenario Matrix

| Viewport | Surface | Expected |
|---|---|---|
| 1440x900 | Overview | Revenue contained; no sibling intersection |
| 1440x900 | Cost | Currency remains readable inside card |
| 390x844 | Crop/Data Quality | Contract fields use readable rows |
| 390x844 | Administration | Both tab labels fully visible |

## Implementation Steps

1. Add focused Playwright geometry/layout assertions.
2. Adjust grid proportions, intrinsic sizing, and responsive breakpoints.
3. Run contracts, unit tests, typecheck, lint, and targeted browser checks.
4. Inspect screenshots at original resolution.

## Refactor

Use CSS-only fixes unless semantics are required for stable testing. Avoid
compact-number formatting or value truncation because those weaken evidence.

## Tests After

- Repeat all focused assertions at desktop and mobile sizes.
- Run existing overflow and accessibility contracts unchanged.

## Dependency Map

Phase 1 defect evidence → tests first → CSS fixes → Phase 3 capture.

## Success Criteria

- [x] Regression checks fail against the old layout and pass after the fix.
- [x] Exact KPI values and semantic labels remain unchanged.
- [x] No page-level overflow or hidden interactive content.

## Risk Assessment

New geometry checks can be brittle. Scope them to semantic containers and
invariants (containment/non-intersection), not exact pixel snapshots.
