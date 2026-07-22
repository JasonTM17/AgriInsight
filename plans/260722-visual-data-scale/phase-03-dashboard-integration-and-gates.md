# Phase 03 — Dashboard integration and gates

Status: completed 2026-07-22

## Goal

Integrate visuals as contextual evidence, not decorative clutter, while keeping
the dashboard testable and the future React boundary explicit.

## Files

- `dashboard/page-visuals.py` or the repository-conventional equivalent
- `dashboard/app.py`
- `dashboard/cost_analysis_page.py` when route-local placement is needed
- `tests/test_dashboard.py`
- `tests/test_visual_assets.py`
- relevant README/docs after verification

## Implementation

1. Add one small visual catalog/helper outside the already-large `app.py`.
2. Render one contextual visual per dashboard area with a concise caption.
3. Keep crop-health provenance visible and avoid observation-ID linkage.
4. Provide a safe missing-asset fallback that explains the unavailable visual
   without breaking charts, filters, or exports.
5. Test all six navigation routes, asset decoding/catalog integrity, captions,
   missing-file behavior, and existing Cost Analysis submit boundaries.

## Quality gates

- Focused dashboard and asset tests.
- Full `python -m pytest` and `python -m compileall -q src dashboard tests`.
- Big-data run evidence and disk guard before/after.
- CK frontend anti-slop/accessibility review: meaningful imagery, no image-only
  status, readable captions, no layout-dependent correctness.
- `git diff --check`, docs validation, and small conventional commits.

## Future web handoff

The future Next.js app should use responsive `srcset`, native lazy loading,
descriptive `alt`, and image optimization. This Streamlit integration does not
authorize browser token storage or client-side KPI recomputation.

## Evidence

- Focused visual/dashboard suite: 13 passed; full Python regression: 75 passed,
  3 intentional skips; compileall passed.
- Live browser gate with the big-data artifacts rendered Executive and Crop
  Health; the Crop Health warning and page visual were visible.
- Theme config is covered by TOML assertions and uses the Field Ledger tokens.
