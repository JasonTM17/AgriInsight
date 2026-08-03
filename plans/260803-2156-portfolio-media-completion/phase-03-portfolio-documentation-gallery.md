---
phase: 3
title: "Portfolio documentation gallery"
status: pending
effort: "medium"
---

# Phase 3: Portfolio documentation gallery

## Overview

Make the repository immediately legible as a portfolio reference while keeping real UI evidence distinct from contextual AI artwork.

## Implementation Steps

1. Lead README presentation with a current real Overview screenshot and add a concise desktop/mobile product tour for the seven core surfaces.
2. Render all eight approved files from `dashboard/assets/generated/` in a separately labeled contextual imagery gallery.
3. Preserve Inventory and Yield forecast proof, remove duplicate or stale media claims, and keep the pre-production positioning explicit.
4. Synchronize public documentation where media provenance, local operation, design boundaries, or codebase inventory changed.

## Files

- `README.md`
- `docs/design-guidelines.md`
- `docs/reporting-and-local-operations.md`
- `docs/codebase-summary.md`
- `docs/assets/generated/README.md`

## Success Criteria

- [ ] README references all fourteen hosted core images and all eight contextual WebPs with no broken local links.
- [ ] Labels prevent contextual artwork from being mistaken for telemetry or live UI evidence.
- [ ] Core product flow is understandable on desktop and mobile without expanding feature claims.
- [ ] Documentation claims match code, capture catalog, and current pre-production status.
- [ ] Markdown/media link validation passes.

## Risks and Rollback

- Avoid an oversized README by using compact tables/details sections while keeping critical proof visible above the fold.
- Documentation changes can be reverted independently from generated media.
