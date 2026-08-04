---
phase: 3
title: Recapture verified product media
status: completed
priority: P1
effort: 4h plus hosted CI
dependencies:
  - 2
---

# Phase 3: Recapture verified product media

## Overview

Regenerate real UI media through the existing authenticated capture pipeline,
review every frame, and update provenance only from a passing hosted artifact.

## Requirements

- Never retouch screenshots to hide UI defects.
- Preserve source commit, run URL, viewport, dimensions, bytes, and SHA-256.
- Forecast evidence must be framed/captioned as an inner scrollable panel, not a full page.

## File Inventory

| Action | Path | Test impact |
|---|---|---|
| Modify | `web/tests/capture/*.spec.ts` | Capture framing and assertions |
| Modify | `scripts/build-demo-media.ps1` | Canonical conversion/catalog input if needed |
| Replace | `docs/assets/screens/*` | Hosted real-product evidence only |
| Replace | `assets/generated/*.gif` | Only when new hosted frames pass review |
| Modify | `docs/assets/screens/catalog.json` | Generated hosted provenance |

## Test Scenario Matrix

| Evidence | Expected |
|---|---|
| Portfolio pair | Desktop/mobile, readable, no overlap |
| Forecast panel | Clear scroll context; no fake full-page framing |
| Catalog | Exact path/dimensions/hash/run identity |
| GIF | All frames reviewed; no overlay/crop ambiguity |

## Implementation Steps

1. Run local disk guard and the feasible capture gate.
2. Push the implementation branch and wait for hosted browser CI.
3. Download the exact-head media artifact; verify source SHA and run success.
4. Import through the existing conversion script, inspect every result, and
   commit only reviewed assets/catalog.

## Dependency Map

Phase 2 green layout → hosted capture → verified artifact → docs integration.

## Success Criteria

- [x] All imported media traces to one successful exact-head run.
- [x] Every frame reviewed at original resolution.
- [x] Catalog and binary validators pass.
- [x] No stale or misleading README-visible screenshot remains.

## Risk Assessment

Hosted capture may be unavailable or storage constrained. Do not replace
provenance with local claims; keep the phase open and report the exact blocker.
