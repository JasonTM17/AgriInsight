---
phase: 4
title: "Protected release and package publication"
status: completed
priority: P1
effort: "6h"
dependencies:
  - 3
---

# Phase 4: Protected release and package publication

## Overview

Publish the accepted feature as the next semantic GitHub release and the same
four existing application images to Docker Hub and GHCR. Release governance is
kept separate from feature correctness so an owner approval delay cannot
misrepresent Phase 3 acceptance.

## Requirements

- Choose the semantic version from repository history; a new additive public
  forecast endpoint/UI normally advances the minor version.
- Tag only an exact `main` head whose full 10-job CI run passed.
- Use the protected `publish-images.yml` workflow and `release-images`
  environment; never bypass reviewers or expose credentials.
- Publish Python, backend, web, and analytics-api images only. Do not introduce
  a fifth service/image.
- Publish semantic and full-SHA tags, never `latest`.
- Require SBOM/provenance, vulnerability scan, non-root/read-only smoke, and
  byte-identical Docker Hub/GHCR digest parity for every image.
- Create a GitHub Release with verified yield UI still/GIF assets and checksums.
- Update release/deployment/roadmap evidence only after immutable publication
  succeeds. External VPS deployment remains open.

## Related files

- Read/verify: `.github/workflows/publish-images.yml`
- Read/verify: `tests/test_container_release_contract.py`
- Read/verify: `scripts/smoke-image-digest.ps1`
- Update after publication: `README.md`, `docs/deployment-guide.md`,
  `docs/project-roadmap.md`, `docs/project-overview-pdr.md`,
  `docs/codebase-summary.md`, and the Phase 3/4 acceptance reports
- Create: `reports/phase-04-protected-release-evidence.md`

## Implementation Steps

1. Ensure the feature head is merged/pushed on `main`, the worktree contains
   only the four preserved user-untracked roots, and local C/D disk guards are
   healthy.
2. Run or verify exact-head 10-job hosted CI. Do not create a tag while any job
   is missing, pending, skipped unexpectedly, or failed.
3. Create/push the annotated semantic tag at that exact head and create the
   GitHub Release with verified media assets and SHA-256 values.
4. Let the protected workflow publish serially. Approve only through the normal
   repository environment path.
5. Verify all semantic/full-SHA references, published digests, registry parity,
   scans, SBOM/provenance, and non-root/read-only smoke evidence.
6. Commit/push the post-release documentation evidence and verify its CI.

## Success Criteria

- [x] Release tag resolves to the exact CI-verified `main` head containing the
  accepted Phase 3 merge.
- [x] Exact-head CI passes all 10 jobs before tagging.
- [x] Protected publication passes all four image jobs through normal
  environment approvals without bypass.
- [x] All 16 registry references resolve to the four recorded immutable
  digests and Docker Hub/GHCR parity is proven.
- [x] GitHub Release contains verified yield still/GIF assets and hashes.
- [x] Docs distinguish hosted release publication from external deployment and
  real agronomic/model SLA.
- [x] Post-release documentation commit passes hosted CI before Phase 4 is
  marked completed.

## Acceptance evidence

- Annotated tag object `4c27b343eecd32cf7daac462e5f661011e2af0df`
  peels to exact `main` SHA
  `616527dcc7f4a03720fb48e617f9310ab9614873`. Exact-head CI
  [`30697294137`](https://github.com/JasonTM17/AgriInsight/actions/runs/30697294137)
  passed 10/10 before tagging.
- Protected publication
  [`30697808763`](https://github.com/JasonTM17/AgriInsight/actions/runs/30697808763)
  passed 4/4. Independent inspection proved all 16 semantic/full-SHA Docker
  Hub/GHCR references resolve to the four digests in
  [the release report](./reports/phase-04-protected-release-evidence.md).
- Public GitHub Release
  [`v0.4.0`](https://github.com/JasonTM17/AgriInsight/releases/tag/v0.4.0)
  contains the accepted desktop/mobile WebP and GIF with matching byte counts
  and SHA-256 values.
- Post-release documentation commit
  `337391bc76dd82123dc12af7f22e039884df28f2` passed all 10 jobs in hosted CI
  [`30699659447`](https://github.com/JasonTM17/AgriInsight/actions/runs/30699659447).
  Phase 4 is completed on that immutable evidence.

## Risks and rollback

- Registry reviewer or credential failure blocks this phase only, not accepted
  source behavior.
- Never move/reuse a published semantic tag. A failed release is corrected with
  a new patch version after a fresh exact-head CI run.
- Rollback deploys the prior semantic image set and its matching Gold artifacts.

## Security

No secret value is read, printed, written to `.env`, staged, or committed.
Repository/environment secrets remain inside GitHub Actions.
