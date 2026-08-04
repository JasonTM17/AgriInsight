---
title: Portfolio media completion
description: >-
  Create verified real-product media for the core portfolio surfaces, publish a
  truthful README gallery, and close the GitHub social-preview handoff.
status: completed
priority: P1
branch: portfolio/complete-media
tags:
  - portfolio
  - media
  - playwright
  - github
blockedBy: []
blocks: []
created: '2026-08-03T15:03:43.761Z'
createdBy: 'ck:plan'
source: skill
---

# Portfolio media completion

## Overview

Finish the pre-production portfolio media layer without widening product scope. Capture the real hosted CI stack for seven core surfaces at desktop and mobile sizes, preserve a hard boundary between real UI evidence and AI-generated contextual artwork, then publish and verify the repository presentation.

## Acceptance Criteria

- Fourteen canonical hosted UI WebPs exist for Overview, Work, Cost Analysis, Crop Health, Data Quality, Assistant, and Administration.
- Every hosted image is traceable to a GitHub Actions run and validated for path, dimensions, byte size, and SHA-256 digest.
- README renders all eight approved contextual WebPs with an explicit contextual/AI label and a separate real-product gallery.
- Assistant imagery shows the evidence-first initial workspace only; no provider response is fabricated.
- GitHub social preview uses the tracked preview asset and is verified from repository metadata.
- Focused media tests, web validation, hosted browser CI, and final protected-branch checks pass.

## Scope Boundary

- Included: capture contract, CI artifact publication, media import, README/gallery docs, social-preview verification.
- Excluded: production deployment, new ML/RAG behavior, realtime gallery expansion, provider credentials, and UI redesign.

## Phases

| Phase | Name | Status |
|-------|------|--------|
| 1 | [Capture contract and media validation](./phase-01-capture-contract-and-media-validation.md) | Completed |
| 2 | [Hosted product capture and import](./phase-02-hosted-product-capture-and-import.md) | Completed |
| 3 | [Portfolio documentation gallery](./phase-03-portfolio-documentation-gallery.md) | Completed |
| 4 | [Social preview and acceptance](./phase-04-social-preview-and-acceptance.md) | Completed |

## Dependencies

- No blocking plan dependency. The Assistant capture intentionally covers the current evidence-first empty state and does not depend on the unfinished provider/RAG plan.
- Hosted browser CI is required because the local machine does not have reliable capacity for the complete integration stack.

## Delivery Strategy

1. Land the capture contract and fail-closed validation on the feature branch.
2. Run the hosted stack, download the immutable artifact, and import only validated outputs.
3. Update public documentation after real images exist.
4. Upload the social preview through GitHub's authenticated owner UI, verify metadata, review, and merge through the protected flow.
