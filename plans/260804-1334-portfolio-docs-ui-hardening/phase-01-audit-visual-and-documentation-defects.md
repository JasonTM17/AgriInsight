---
phase: 1
title: Audit visual and documentation defects
status: completed
priority: P1
effort: 2h
dependencies: []
---

# Phase 1: Audit visual and documentation defects

## Overview

Create a complete evidence matrix for every README image and evergreen doc,
separating application defects from capture-framing and Markdown presentation.

## Requirements

- Inspect every committed WebP/GIF/PNG embedded by `README.md`.
- Trace visible defects to exact component/CSS/capture code.
- Verify internal Markdown links and current architecture claims against source.

## File Inventory

| Action | Path | Test impact |
|---|---|---|
| Read | `README.md`, `docs/**/*.md` | Establish public contract |
| Read | `docs/assets/**`, `assets/generated/**` | Visual/provenance inventory |
| Read | `web/src/**`, `web/tests/capture/**` | Root cause and missing gates |
| Update | This plan and reports only | Record evidence before fixes |

## Test Scenario Matrix

| Priority | Scenario | Expected |
|---|---|---|
| Critical | README image target missing | Fail documentation gate |
| High | Text overlaps sibling KPI | Fail capture gate |
| High | Mobile tab label clipped | Fail capture gate |
| Medium | Intentional inner table scroll | Accept only with clear framing/caption |

## Implementation Steps

1. Enumerate README media references and inspect original-resolution frames.
2. Compare every suspected defect with DOM/CSS and capture behavior.
3. Audit doc ownership, duplicate claims, stale architecture, and links.
4. Classify each issue as UI, capture, README layout, or documentation drift.

## Dependency Map

`committed media + docs + source` → `verified defect matrix` → Phases 2-4.

## Success Criteria

- [x] Every public image classified with evidence.
- [x] Root cause recorded for every fix candidate.
- [x] No defect inferred from scaling alone.
- [x] Documentation ownership map agreed from repository evidence.

## Risk Assessment

Visual review is subjective. Mitigate with bounding-box evidence, original
resolution inspection, and explicit distinction between clipping and scrollable content.
