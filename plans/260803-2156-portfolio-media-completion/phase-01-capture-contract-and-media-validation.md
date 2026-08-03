---
phase: 1
title: Capture contract and media validation
status: in-progress
effort: medium
---

# Phase 1: Capture contract and media validation

## Overview

Define deterministic filenames, routes, personas, viewports, provenance, and fail-closed tests before generating portfolio evidence.

## Implementation Steps

1. Extend `web/tests/capture/demo-media.spec.ts` with desktop and mobile captures for the seven required surfaces, using stable readiness markers and existing OIDC personas.
2. Extend `scripts/build-demo-media.ps1` to require the canonical PNG inputs, convert them to WebP, and emit a provenance catalog containing GitHub run metadata and file integrity fields.
3. Upload the complete capture set from `.github/workflows/ci.yml` as a required artifact.
4. Add focused Python and shell tests that reject missing, stale, mislabeled, or contextual images presented as hosted product evidence.

## Files

- `web/tests/capture/demo-media.spec.ts`
- `scripts/build-demo-media.ps1`
- `.github/workflows/ci.yml`
- `tests/test_portfolio_media.py`
- `web/tests/shell/platform-e2e-runner.test.ts`

## Success Criteria

- [ ] The capture spec covers exactly fourteen canonical desktop/mobile outputs.
- [ ] Each route uses the correct least-privilege persona and stable page marker.
- [ ] Missing capture inputs fail the media build instead of preserving stale evidence.
- [ ] CI uploads both source PNGs and optimized WebPs with `if-no-files-found: error`.
- [ ] Focused tests and relevant type/lint checks pass.

## Risks and Rollback

- Administration data may contain time-varying rows; capture a stable filtered/upper-page state and never expose credentials or personal data.
- Revert this phase as one focused commit if hosted CI proves the capture contract invalid.
