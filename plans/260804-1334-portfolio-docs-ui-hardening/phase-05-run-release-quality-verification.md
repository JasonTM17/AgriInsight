---
phase: 5
title: Run release-quality verification
status: completed
priority: P1
effort: 3h plus hosted CI
dependencies:
  - 2
  - 3
  - 4
---

# Phase 5: Run release-quality verification

## Overview

Run focused and broad gates, perform adversarial code/doc/media review, and land
the work only when the exact head and imported media evidence agree.

## Requirements

- No hidden skipped failures or stale CI claims.
- Review public-contract, accessibility, responsive, and provenance risks.
- Merge and clean the feature branch only after all required checks pass.

## File Inventory

| Action | Path | Test impact |
|---|---|---|
| Read | All changed files and `git diff` | Scope/review |
| Update | Plan phase states/reports | Evidence only |
| Git | Feature branch/PR | No force push |

## Test Scenario Matrix

| Gate | Expected |
|---|---|
| Web contracts/unit/type/lint/build | Pass |
| Python media/docs tests | Pass |
| SVG/XML/PNG inspection | Pass |
| Hosted CI/browser/media | Pass on exact head |
| Git status/branch cleanup | Clean and merged |

## Implementation Steps

1. Run narrow regression tests, then broad web and docs/media gates.
2. Run code review over CSS, geometry checks, capture, and documentation claims.
3. Push, open/update PR, wait for exact-head CI, and address failures.
4. Merge only after green checks; synchronize local main and clean merged branch.
5. Close the plan with commit hashes, run IDs, media provenance, limitations,
   and unresolved questions.

## Dependency Map

All implementation phases → local review → hosted exact-head evidence → merge.

## Success Criteria

- [x] Focused and broad local gates pass or honest limitations are recorded.
- [x] Exact-head hosted CI and media provenance pass.
- [x] Review has no unresolved high/critical finding.
- [x] Main is synchronized, worktree clean, merged branch removed.

## Risk Assessment

Heavy gates may exceed local disk capacity. Hosted CI can supply integration
evidence, but it cannot excuse failing focused local tests or unreviewed media.
