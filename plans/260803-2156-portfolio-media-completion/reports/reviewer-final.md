# Portfolio Media Final Code Review

## Findings

### Medium

1. **The Assistant no-query guarantee is not behaviorally enforced.**
   **Location:** `web/tests/capture/portfolio-media.spec.ts:108`, `tests/test_portfolio_media.py:91`, `web/tests/shell/platform-e2e-runner.test.ts:119`
   The hosted capture navigates to `/assistant` and waits for `networkidle`, but it never records or rejects requests to the Assistant query endpoint. Both guard tests only search the capture source for the string `queryAssistant`. A future mount-time fetch, renamed client helper, or indirect request could consume the provider path and still satisfy these tests; the screenshot could then contain provider-derived content while the evidence is labeled initial/no-query.
   **Fix:** instrument Playwright request events before navigation and assert zero Assistant query POSTs for both viewports; also assert the response panel remains in its exact initial state before each screenshot.

2. **The mobile overflow gate treats clipping as success and omits a page-level width invariant.**
   **Location:** `web/tests/capture/portfolio-media.spec.ts:49`
   Any descendant outside the viewport is ignored when an ancestor has `overflow-x: hidden` or `clip`, and line 77 returns `fits` without asserting `documentElement.scrollWidth`/`body.scrollWidth`. This permits cut-off content or pseudo-element overflow to pass the stated mobile guard. Current screenshots show no page-level overflow; this is a false-negative path in the reusable acceptance check.
   **Fix:** assert root/body scroll width does not exceed client width, allow only reviewed `auto`/`scroll` containers, and reject `hidden`/`clip` containment unless the clipped element is explicitly allowlisted and non-interactive.

### Low

3. **Phase 1's completed record does not match the landed files or its own checklist.**
   **Location:** `plans/260803-2156-portfolio-media-completion/phase-01-capture-contract-and-media-validation.md:4`, `:16`, `:23`, `:31`
   The phase is marked completed, but it says `demo-media.spec.ts` was extended while the branch added `portfolio-media.spec.ts`, and all five success criteria remain unchecked. This makes plan completion audit unreliable even though the implementation largely satisfies the criteria.
   **Fix:** have the plan owner reconcile the actual capture file and verified criteria after final CI; do not change plan status from this review.

## Code Review Summary

### Scope

- Base/head: `origin/main` (`02a87666`) ... `1dad8c4b`
- Files: 41 changed; 1,100 insertions, 50 deletions; 14 new hosted WebPs
- Focus: complete portfolio media branch, plan/spec compliance, critical + informational checklist passes, adversarial review
- Scout findings: capture pipeline spans workflow, real-OIDC Playwright, PowerShell builder, catalog, committed binaries, README/docs, and auth return-path policy

### Overall Assessment

No Critical or High findings. Security-sensitive changes are additive and remain fail-closed: `/assistant` is added only to the existing same-origin return-path allowlist; external/protocol-relative returns still resolve to `/overview`. Capture uses four real OIDC personas, a single Playwright worker, and no new API, database, dependency, secret, or production-deployment path.

Do not merge yet. PR #24 exact-head CI run `30873287616` is still in progress at review time; Python, Java, web, and security passed, while realtime/browser gates are running. PR merge state is `BLOCKED` until required checks complete.

### Critical Issues

None.

### High Priority

None.

### Medium Priority

- Add behavioral no-query proof for the Assistant capture.
- Tighten the horizontal-overflow detector against clipping false negatives.

### Low Priority

- Reconcile the Phase 1 completed record with the actual file and checked criteria.

### Edge Cases Found by Scout

- Assistant response can regress through an indirect or renamed request because current guards inspect source text, not browser traffic.
- `overflow-x: hidden|clip` and pseudo-element/root overflow can evade the viewport offender scan.
- Missing canonical capture PNGs throw before conversion; failed capture/build prevents artifact upload. Existing source PNGs are not deleted by builder cleanup.
- Builder conversion and catalog generation are sequential; no shared mutable or parallel catalog race found.
- Return-path parsing rejects backslashes, protocol-relative URLs, foreign origins, and non-allowlisted paths; arbitrary query data stays same-origin and is not interpreted by the Assistant route.

### Verified Evidence and Non-Findings

- Visually inspected all 14 committed WebPs. No browser chrome, secret, credential, customer PII, real-person email, or provider answer found. Administration exposes only the synthetic `tenant-admin@demo.invalid` fixture.
- Assistant desktop/mobile show the initial evidence-first workspace only. Surrounding component code sends a query only after form/suggestion interaction.
- Hosted catalog contains exactly 14 entries. Committed byte sizes, dimensions, paths, personas, routes, and SHA-256 digests pass `tests/test_portfolio_media.py`.
- Actions run `30868766788` is successful; its branch head is `0c8f3ff7`, and the catalog/report consistently identify the PR merge SHA `2662928a...` used as `GITHUB_SHA`.
- README references exactly 14 hosted screenshots and all 8 contextual WebPs. Contextual catalog/hash tests pass, and Crop Health remains explicitly labeled AI-generated demo evidence.
- Public docs preserve the portfolio/pre-production boundary and do not claim production telemetry, provider SLO/quality, agronomic ground truth, or external deployment.
- No concurrency, N+1 query, database schema, authz, raw HTML, input-parsing, or data-exposure regression is introduced by the diff.

### Plan Status Review

- Phase 1: implementation present; phase record needs bookkeeping correction noted above.
- Phase 2: hosted capture/import evidence present and externally cross-checked.
- Phase 3: 14 hosted + 8 contextual references and evidence boundaries present.
- Phase 4: correctly remains in progress. Social-preview verification report, final exact-head green CI, protected merge, branch cleanup, and default-branch alignment are not complete in this branch snapshot.

### Recommended Actions

1. Wait for exact-head run `30873287616`; do not merge unless every required check succeeds.
2. Add runtime zero-query assertion to Assistant capture before treating the no-query contract as regression-proof.
3. Tighten the overflow guard or explicitly accept the residual false-negative risk.
4. Reconcile Phase 1 file/checklist claims and complete Phase 4 evidence through the plan owner.

### Metrics

- Type coverage: not measured; current-head `npm --prefix web run typecheck` passed
- Test coverage: not measured
- Focused media: 3 passed
- Contextual media: 7 passed
- Focused web contracts: 24 passed across 3 files
- Linting issues: 0 reported by the successful current-head web CI job
- Diff check: clean

### Unresolved Questions

- Will exact-head run `30873287616` complete all realtime, browser, media-builder, and four image jobs successfully?
- Has the public GitHub social-preview image been byte/visual-verified against `docs/assets/agriinsight-social-preview.jpg` and recorded in the planned evidence report?

Status: DONE_WITH_CONCERNS
Summary: No Critical/High code defect; two Medium acceptance-test gaps and one Low plan-record mismatch. Merge remains gated on current-head CI.
Concerns/Blockers: Exact-head CI pending; Phase 4 social-preview/merge/default-branch evidence incomplete.
