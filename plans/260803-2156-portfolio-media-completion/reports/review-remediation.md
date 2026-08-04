# Final review remediation

## Review baseline

The independent review of `origin/main...1dad8c4b` reported no Critical or High
finding. It identified two Medium capture-contract gaps and one Low plan-record
mismatch. All three were remediated before merge.

## Resolutions

1. Assistant no-query proof now listens to real Playwright request events during
   each capture. Any `POST /api/assistant/query` fails assertions both before
   and after each screenshot, and the initial evidence-first heading must remain
   visible at both viewports.
2. The horizontal-overflow gate now rejects root or body scroll width beyond the
   viewport. Bounded `auto`/`scroll` regions remain allowed. `hidden`/`clip`
   containment fails immediately unless its ancestor explicitly sets
   `data-portfolio-capture-clip="non-interactive"` and the clipped subtree has
   no interactive relationship. The detector scans every ancestor before
   approving a reviewed scroll boundary, so an outer invalid clip cannot be
   bypassed by an inner `auto`/`scroll` container.
3. Phase 1 now names the landed `portfolio-media.spec.ts` file and records its
   five verified success criteria.

The Administration tab strip remains a deliberate bounded horizontal scroller
on mobile. It is interactive and accessible by swipe/keyboard; it does not
create page-level overflow. The strengthened gate no longer treats a hidden or
clipped interactive descendant as acceptable.

## Focused validation

```text
python -m pytest tests/test_portfolio_media.py -q
3 passed

npm exec -- vitest run tests/shell/platform-e2e-runner.test.ts
11 passed

npm run typecheck
passed

npm run lint
passed, zero warnings

node node_modules/@playwright/test/cli.js test \
  --config=playwright.capture.config.ts --list
11 capture tests discovered in 3 files
```

The new runtime assertions still require the hosted seven-persona browser gate
on the remediation commit before merge.

## Unresolved questions

None for the review findings. Social-preview verification remains owned by
Phase 4.
