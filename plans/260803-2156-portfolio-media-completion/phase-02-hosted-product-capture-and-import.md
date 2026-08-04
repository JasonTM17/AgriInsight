---
phase: 2
title: Hosted product capture and import
status: completed
effort: medium
---

# Phase 2: Hosted product capture and import

## Overview

Generate the screenshots from the real GitHub Actions integration stack, validate the artifact, and import the optimized evidence without local mock substitutions.

## Implementation Steps

1. Push the capture-contract commit and wait for the hosted `browser-e2e` job.
2. Download the artifact into an explicit temporary workspace, verify its run/commit identity, and inspect all fourteen images.
3. Import canonical WebPs under `docs/assets/screens/` and generate `catalog.json` from the same artifact metadata.
4. Add `docs/assets/screens/README.md` explaining capture provenance, evidence limits, and regeneration.

## Files

- `docs/assets/screens/*.webp`
- `docs/assets/screens/catalog.json`
- `docs/assets/screens/README.md`
- `plans/260803-2156-portfolio-media-completion/reports/hosted-media-capture.md`

## Success Criteria

- [x] Hosted run is green for the capture commit.
- [x] All fourteen WebPs visually match the intended real application routes.
- [x] Catalog digests, dimensions, sizes, commit SHA, run ID, and run URL match the downloaded artifact.
- [x] No screenshots contain secrets, credentials, accidental PII, browser chrome, or fabricated provider output.
- [x] Media validation passes after importing the immutable artifact.

## Risks and Rollback

- Treat incomplete or visually corrupted artifacts as failed evidence and rerun CI; never fill gaps with mockups.
- Imported binaries are removable as one focused commit if provenance cannot be verified.
