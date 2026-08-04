# Portfolio Media Completion Postmortem

**Date**: 2026-08-04 10:18
**Severity**: Medium
**Component**: portfolio media capture, README gallery, GitHub social preview
**Status**: Blocked

## What Happened

We finished the portfolio media slice on branch `portfolio/complete-media`, but only after multiple correction passes. The hosted capture was proven on GitHub Actions run `30868766788` and the post-import CI stayed green on `30870554268`, using merge SHA `2662928a6399a9be8ff9be7a60dfb2ae40b2b03d` with branch head `0c8f3ff710a1907ca375577d0433eed20a4ba526`. The media contract initially missed two real problems: source capture files were at risk during cleanup, and the first mobile overflow guard was too weak to prove containment.

## The Brutal Truth

The first version of the gate checked the wrong things. It trusted filenames and source text more than browser traffic and page geometry, which is exactly how the Assistant no-query boundary and the mobile clipping bug survived the first pass. The work only closed after review forced the checks to become behavioral instead of textual.

## Technical Details

- `0c8f3ff7` fixed the source-deletion bug by preserving capture inputs and only removing generated outputs/catalog data.
- The mobile containment path was iterated through `b84687c2`, `5a7dbd2f`, `987a62b8`, `b1fd23a0`, and `e5564859` before the boundary was stable.
- `a5b05030` hardened the final capture boundary checks.
- `2a051c64` preserved the Assistant login return path without widening trust.
- `1dad8c4b` corrected the README gallery so the real hosted screenshots and the eight contextual AI visuals render from verified local assets.
- `tests/test_portfolio_media.py` passed `3 passed`; hosted capture evidence confirmed 14 WebPs, exact dimensions, and matching SHA-256 digests.

## What We Tried

We first used source-level checks and file presence validation. That was not enough. Review correctly pushed the contract toward runtime Playwright request assertions, root/body scroll-width checks, and exact digest comparison against the imported artifact catalog.

## Root Cause Analysis

The root cause was weak evidence. We treated static references as proof of behavior. That was wrong for both the Assistant no-query path and responsive containment. The browser upload step remained separate because GitHub still exposes no supported social-preview upload API; phase 4 depends on the authenticated owner-only Settings UI, and the plan says to stop if that browser session is unavailable.

## Lessons Learned

Behavioral guarantees need behavioral tests. If a contract matters, assert it at runtime: browser requests, rendered geometry, digest identity, and actual metadata, not just source strings.

## Next Steps

1. Record the GitHub social-preview upload in `plans/260803-2156-portfolio-media-completion/reports/social-preview-verification.md` once the owner browser session is available.
2. Keep the capture boundary tests in place; do not relax the request-event or scroll-width assertions.
3. Preserve the source/output split in the media builder so cleanup never deletes inputs again.
4. Re-run final hosted verification after the social-preview step closes.
