---
phase: 4
title: "Social preview and acceptance"
status: pending
effort: "small"
---

# Phase 4: Social preview and acceptance

## Overview

Close the owner-only GitHub presentation step, run independent validation and review, merge through protection, and verify the exact default-branch result.

## Implementation Steps

1. Upload `docs/assets/agriinsight-social-preview.jpg` through the authenticated GitHub repository Settings UI.
2. Verify the public repository `og:image`/`twitter:image` metadata after cache propagation and replace the old owner-handoff note with evidence.
3. Run the full relevant local gates plus hosted CI, independent testing/debug review, and code review.
4. Commit in focused slices, open/update the protected pull request, merge after green checks, delete the merged branch, and verify clean local/default-branch alignment.

## Files

- `plans/260803-2156-portfolio-media-completion/reports/social-preview-verification.md`
- `plans/260722-2342-production-web-platform/reports/github-social-preview-owner-handoff.md` (retire or mark superseded)
- Plan status/report files required by the CK workflow

## Success Criteria

- [ ] GitHub repository settings show the tracked social-preview asset.
- [ ] Public repository metadata resolves to the uploaded preview image.
- [ ] Test, review, security-sensitive media checks, and hosted CI are green on final HEAD.
- [ ] Pull request is merged through protection; merged branch is removed locally and remotely.
- [ ] `main` is clean and exactly aligned with `origin/main`.

## Risks and Rollback

- GitHub exposes no supported API for social-preview upload; stop and report only if the existing authenticated owner browser session is unavailable.
- Cache propagation may delay public metadata; record both settings confirmation and eventual public verification.
