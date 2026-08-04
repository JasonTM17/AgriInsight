---
phase: 4
title: Restructure portfolio documentation
status: completed
priority: P1
effort: 5h
dependencies:
  - 1
  - 3
---

# Phase 4: Restructure portfolio documentation

## Overview

Turn README into a focused portfolio landing page and make `docs/index.md` plus
`docs/system-architecture.md` the canonical navigation and architecture layer.

## Requirements

- Lead with product outcome, architecture, verified status, and local quick start.
- Keep detailed release history and operations out of README.
- Add publish-grade system context/runtime/trust visuals with source SVG and PNG.
- Preserve portfolio/pre-production and external NO-GO language.

## File Inventory

| Action | Path | Test impact |
|---|---|---|
| Rewrite | `README.md` | Links/media/docs landing contract |
| Create | `docs/index.md` | Canonical documentation map |
| Modify | `docs/system-architecture.md` | Current architecture source of truth |
| Modify | `docs/architecture.md` | Mark analytics-only legacy scope, link canonical doc |
| Modify | `docs/codebase-summary.md`, `docs/design-guidelines.md` | Remove stale media/license facts |
| Create | `docs/assets/agriinsight-system-architecture.svg/.png` | Publish-grade architecture |
| Create | `docs/assets/agriinsight-security-boundary.svg/.png` | Trust boundary |

## Test Scenario Matrix

| Scenario | Expected |
|---|---|
| New visitor | Understands product, stack, status, and run path quickly |
| Maintainer | Finds canonical doc without duplicated policy hunting |
| GitHub render | Images and diagrams fit without clipping |
| Production reader | Sees explicit pre-production/NO-GO boundary |

## Implementation Steps

1. Generate current system and trust-boundary SVGs using verified components.
2. Validate SVG, export PNG, and visually inspect arrow/text layout.
3. Rewrite README with concise sections and curated verified media only.
4. Add `docs/index.md`; reconcile stale/duplicate architecture claims.
5. Verify every command, path, link, and status claim against repository evidence.

## Dependency Map

Phase 1 doc audit + Phase 3 reviewed media → canonical docs/diagrams → Phase 5.

## Success Criteria

- [x] README functions as a professional portfolio landing page.
- [x] System and trust diagrams render cleanly as SVG and PNG.
- [x] Legacy architecture wording no longer contradicts current topology.
- [x] All internal links and image references resolve.

## Risk Assessment

Aggressive shortening can erase important evidence. Preserve detail in the
correct canonical docs and link it; do not duplicate it back into README.
