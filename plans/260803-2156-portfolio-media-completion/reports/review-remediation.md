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
   containment before a scrollport fails unless its ancestor explicitly sets
   `data-portfolio-capture-clip="non-interactive"` and the clipped subtree has
   no interactive relationship. The detector scans every ancestor. An outer
   decorative clip may follow a reviewed scrollport only when it contains the
   entire bounded scrollport; a narrower outer clip still fails.
3. Phase 1 now names the landed `portfolio-media.spec.ts` file and records its
   five verified success criteria.

The Administration tab strip remains a deliberate bounded horizontal scroller
on mobile. It is interactive and accessible by swipe/keyboard; it does not
create page-level overflow. The strengthened gate does not treat hidden or
clipped interactive content as acceptable unless a complete reviewed
scrollport already owns that content.

## Hosted regression follow-up

Run `30874797666` exposed a false positive in the first full-ancestor
implementation for the mobile Cost and Administration tables. Root width stayed
390px, but their bounded scrollports sit inside decorative hidden panels. The
collector and pure boundary decision are now separated, and regression tests
cover both the valid nested panel shape and a narrower outer-clip bypass. See
`ci-regression-diagnosis.md`. Hosted rerun remains required before merge.

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
