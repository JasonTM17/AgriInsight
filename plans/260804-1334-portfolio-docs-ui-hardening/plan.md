---
title: Portfolio documentation and UI media hardening
description: >-
  Repair public UI evidence, rebuild verified media, and make the repository
  documentation a concise professional portfolio entry point.
status: completed
priority: P1
branch: fix/portfolio-docs-ui-media
tags:
  - bugfix
  - docs
  - frontend
  - critical
blockedBy: []
blocks: []
created: '2026-08-04T06:36:31.038Z'
createdBy: 'ck:plan'
source: skill
---

# Portfolio documentation and UI media hardening

## Overview

Repair defects visible in public portfolio media, prevent regression in the
hosted capture gate, replace misleading legacy captures, and reorganize the
documentation around one current architecture source of truth. Preserve the
honest portfolio/pre-production boundary and real hosted-product provenance.

## Scope Decision

**EXPANSION, controlled:** cover every image currently embedded in `README.md`,
the responsive UI states that produced visible defects, one canonical system
architecture diagram set, and the evergreen documentation navigation layer.
Do not add product features, deploy externally, or fabricate screenshots.

## Verified Starting Findings

- Overview revenue overlaps sibling KPI content in the committed desktop image;
  the lead column is too narrow for the unbroken full VND value.
- Cost KPI currency wraps onto a separate line in the committed desktop image.
- Crop/Data Quality contract metadata is technically contained but breaks into
  hard-to-read fragments on 390 px captures.
- Administration tabs rely on horizontal scrolling and visibly clip the second
  label in the committed mobile image.
- Forecast stills are panel-focused, horizontally scrollable evidence captures;
  embedding them as full-page portfolio screenshots makes correct behavior look broken.
- `docs/architecture.md` describes the legacy Python-only MVP as current while
  `docs/system-architecture.md` documents the actual Python/Spring/Next topology.
- Internal Markdown links currently resolve; the main debt is hierarchy,
  duplication, stale wording, and missing system/deployment/trust diagrams.

## Cross-Plan Dependencies

No blocking dependency. This work must not alter product contracts owned by the
active auth/RAG/production-readiness plans. Documentation updates may link to
those plans but cannot mark their external gates complete.

## Phases

| Phase | Name | Status |
|-------|------|--------|
| 1 | [Audit visual and documentation defects](./phase-01-audit-visual-and-documentation-defects.md) | Completed |
| 2 | [Fix responsive UI contracts](./phase-02-fix-responsive-ui-contracts.md) | Completed |
| 3 | [Recapture verified product media](./phase-03-recapture-verified-product-media.md) | Completed |
| 4 | [Restructure portfolio documentation](./phase-04-restructure-portfolio-documentation.md) | Completed |
| 5 | [Run release-quality verification](./phase-05-run-release-quality-verification.md) | Completed |

## Dependencies

- Real UI and media only; no mock screenshots or hand-edited UI evidence.
- Local focused gates before hosted browser/media capture.
- Hosted capture catalog remains provenance-authoritative.
- Detached recovery worktree remains untouched.

## Acceptance Criteria

- All README-visible images are visually reviewed; misleading images are
  recaptured, reframed, or removed from the landing page.
- Overview, Cost, Crop/Data Quality, and Administration responsive defects have
  executable regression assertions at desktop/mobile capture sizes.
- README is a concise portfolio landing page with an honest status, product
  value, architecture visual, local quick start, verified gallery, and doc map.
- `docs/index.md` provides canonical navigation and system architecture owns the
  current topology, deployment, and trust-boundary diagrams.
- Local checks pass; hosted CI/media artifact passes before committed screenshots
  or provenance claims are updated.
