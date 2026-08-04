---
title: Portfolio UI/media/docs hardening exposed real source defects, not fake polish issues
date: 2026-08-04 14:49
severity: High
component: portfolio UI media / docs / capture provenance
status: Resolved
---

# Portfolio UI/media/docs hardening

## Context

This slice covered the public portfolio surface for AgriInsight: README, docs navigation,
architecture diagrams, responsive UI contracts, and the hosted screenshot catalog. The
goal was not to make the repo look nice. It was to stop misleading evidence from shipping.

## What happened

Two different problems were mixed together at first. Some broken pixels were real source-capture
defects from CSS: the Overview revenue text escaped its KPI card, Cost KPI currency wrapped
badly, and the mobile admin tabs clipped. The forecast images were a framing problem, not a
layout regression: they are inner scrollable evidence panels and were being presented like full
page screenshots, which made correct behavior look broken.

The first geometry assertion was also wrong. It measured the block box, not the rendered glyph
range, so it could miss the exact overflow we were trying to catch. Adversarial self-review
forced the fix: `requiredTextBox()` now uses `document.createRange()`, `range.getBoundingClientRect()`,
and `range.getClientRects()` before the screenshot is taken.

## Failure chain / root causes

1. I trusted visual inspection too much and let source CSS defects reach capture.
2. I framed forecast evidence as a full-page hero instead of a scrollable panel with a caption.
3. The initial geometry test checked the wrong box, so it could bless a broken render.
4. The repo had stale documentation hierarchy, so README and docs needed a real cleanup instead
   of another cosmetic patch.

## Decisions and rejected alternatives

We kept the fixes CSS-only and test-driven. I rejected truncation, compact-number hacks, or fake
screenshots because they would hide the defect instead of fixing it. I also rejected treating
hosted screenshots as production evidence. The new hosted catalog is provenance-only.

## Verification

- Hosted screenshot run: `30885890858`
- Catalog commit SHA: `70d8077f4b405403ab763a1bccc64776a7529643`
- Screenshot set: 14 WebPs in `docs/assets/screens/`
- Capture gate now checks `range.getBoundingClientRect()`, `boxesIntersect(...)`, 44px tab height,
  and mobile contract reflow before writing media.
- Docs now split source of truth correctly: `docs/index.md` is navigation, `docs/system-architecture.md`
  owns current topology and security boundaries, and `docs/architecture.md` is analytics-only.

## Next actions

Keep the production claim at NO-GO. The disk guard still limits how much heavy local verification
we can do, so hosted CI remains the source for media provenance, not a substitute for external
production readiness.

## Unresolved questions

- None.
