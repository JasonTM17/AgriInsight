# Portfolio Media Final Debug Audit

## Executive Summary
- **Issue:** Read-only final audit of portfolio capture/import/docs/auth changes under `portfolio/complete-media`.
- **Impact:** If wrong, repo could publish untraceable screenshots, stale media, broken README evidence, or misleading Assistant/auth behavior.
- **Root cause:** No blocking defect found in the requested change set. The only apparent SHA mismatch is expected GitHub PR-merge behavior: artifact names use merge SHA `2662928a6399a9be8ff9be7a60dfb2ae40b2b03d` while the run head is branch SHA `0c8f3ff710a1907ca375577d0433eed20a4ba526`.
- **Status:** Requested failure modes eliminated with evidence. One non-blocking phase gap remains: social-preview verification evidence is still absent.
- **Fix:** No code/doc fix required for the audited failure modes. Keep phase 4 open until social-preview verification is recorded.

## Scope
- Read: `README.md`, `AGENTS.md`, `CLAUDE.md`, plan/report files, `docs/assets/screens/catalog.json`, `docs/assets/screens/README.md`, `tests/test_portfolio_media.py`, `scripts/build-demo-media.ps1`, `web/tests/capture/portfolio-media.spec.ts`, `.github/workflows/ci.yml`, `web/src/server/auth/request-policy.ts`, `web/src/features/overview/components/overview-farms.module.css`, changed docs, `git diff origin/main...HEAD`.
- Verified against GitHub Actions runs `30868766788` and `30870554268`.
- Did **not** run Docker-heavy Maven `verify` per disk constraint.

## Timeline
- **2026-08-04 01:26:43Z** GitHub created merge commit `2662928a6399a9be8ff9be7a60dfb2ae40b2b03d` for PR run `30868766788`.
- **2026-08-04 01:31:16Z** `browser-e2e` job started on run `30868766788`.
- **2026-08-04 01:39:55Z** real Keycloak/PostgreSQL/Spring/FastAPI/Chrome gate completed successfully.
- **2026-08-04 01:40:07Z** `scripts/build-demo-media.ps1` completed in hosted CI.
- **2026-08-04 01:40:08Z** artifact `portfolio-media-2662928a6399a9be8ff9be7a60dfb2ae40b2b03d` uploaded.
- **2026-08-04 02:02:05Z** post-import CI run `30870554268` started on commit `744588884ea232b21071c28163104a220e4525e3`.
- **2026-08-04 02:15:17Z** post-import hosted browser gate on run `30870554268` completed successfully.

## Hypotheses And Elimination

### Hypothesis 1
Artifact/catalog identity drifted; committed WebPs may not match the hosted artifact because run head SHA and artifact SHA differ.

**Test**
- Read `.github/workflows/ci.yml`: artifact name is `portfolio-media-${{ github.sha }}`.
- Queried `gh run view 30868766788 --json ...`.
- Queried `gh api repos/JasonTM17/AgriInsight/actions/runs/30868766788/artifacts`.
- Queried `gh api repos/JasonTM17/AgriInsight/commits/2662928a6399a9be8ff9be7a60dfb2ae40b2b03d`.
- Downloaded artifact `portfolio-media-2662928a6399a9be8ff9be7a60dfb2ae40b2b03d`.
- Compared downloaded `docs/assets/screens/catalog.json` and all 14 WebPs against committed files.

**Evidence**
- Run `30868766788` is a `pull_request` run with `headSha=0c8f3ff710a1907ca375577d0433eed20a4ba526`.
- GitHub commit API proves merge commit `2662928a...` has parents:
  - base `02a87666fa06e90e86f4354fe895f85de8723448`
  - branch head `0c8f3ff710a1907ca375577d0433eed20a4ba526`
- Artifact API lists `portfolio-media-2662928a6399a9be8ff9be7a60dfb2ae40b2b03d`.
- Downloaded artifact catalog is byte-identical to committed `docs/assets/screens/catalog.json`.
- All 14 artifact WebP SHA-256 digests equal both the catalog digests and the committed binaries.
- Post-import CI run `30870554268` is green on commit `744588884ea232b21071c28163104a220e4525e3`.

**Result**
- Eliminated. No identity drift. The SHA difference is expected PR merge-commit behavior, not a provenance bug.

### Hypothesis 2
The builder/workflow can fail open: stale outputs survive, missing captures still publish, or WebP integrity is not enforced.

**Test**
- Read `scripts/build-demo-media.ps1`, `tests/test_portfolio_media.py`, `.github/workflows/ci.yml`.
- Ran `python -m pytest tests/test_portfolio_media.py -q`.
- Ran `magick identify -format "%f %w %h %n\n" docs/assets/screens/*.webp`.

**Evidence**
- Builder enumerates 14 required portfolio PNG inputs and throws on any missing required capture before conversion.
- Cleanup removes only prior portfolio outputs and `docs/assets/screens/catalog.json`:
  - `Remove-Item -LiteralPath $candidate -Force`
  - `Remove-Item -LiteralPath $portfolioManifestOut -Force`
- The focused test explicitly checks cleanup section does **not** touch `$screensIn`.
- Workflow upload uses `if-no-files-found: error`.
- `pytest` result: `3 passed`.
- `magick identify` shows every audited WebP at exact expected dimensions and single frame:
  - desktop `1280x800`
  - mobile `780x1688`
  - frame count `1`

**Result**
- Eliminated. Current contract is fail-closed for missing portfolio captures and enforces exact imported media integrity.

### Hypothesis 3
The capture/docs/auth changes introduced behavioral regressions: mobile overflow, Assistant provider-query boundary, unsafe return path, broken README media, secrets/PII leakage, or production overclaim.

**Test**
- Read `web/tests/capture/portfolio-media.spec.ts`, `web/src/features/overview/components/overview-farms.module.css`, `web/src/server/auth/request-policy.ts`, `README.md`, changed docs.
- Ran focused web tests from correct cwd:
  - `npm exec vitest run tests/contracts/overview-route.contract.test.ts tests/shell/accessibility.test.ts tests/shell/platform-e2e-runner.test.ts` in `web/`
- Ran canonical web suite:
  - `npm --prefix web test`
- Parsed README image paths and checked file existence.
- Visually inspected:
  - `assistant-evidence-first-desktop.webp`
  - `assistant-evidence-first-mobile.webp`
  - `tenant-administration-desktop.webp`
  - `tenant-administration-mobile.webp`
  - `overview-dashboard-mobile.webp`

**Evidence**
- Capture spec uses `loginWithRealOidc(...)` for least-privilege personas only and never calls `queryAssistant`.
- Assistant screenshots show initial workspace only; no provider answer bubble present in inspected frames.
- Capture spec forces `fullPage: false` and polls for horizontal overflow offenders before screenshot.
- CSS adds `min-width: 0`, `flex-wrap`, bounded chart width, and mobile `minmax(0, 1fr)` grid rules.
- Focused Vitest result: `3 passed`, `24 passed` tests.
- Canonical web suite result: `49 passed`, `1 skipped` files; `396 passed`, `9 skipped` tests.
- `allowlistedReturnPath("/assistant") === "/assistant"` and `allowlistedReturnPath("//evil.example/assistant") === "/overview"`.
- All README local image paths resolve to real files.
- README and changed docs repeatedly state:
  - pre-production / portfolio reference
  - not external production deployment
  - screenshots separate from 8 contextual AI visuals
  - Assistant pair does not prove provider quality/latency/spend/SLO
- Visual inspection found no browser chrome, credentials, tokens, or real-person identity.
- Visible admin address is `tenant-admin@demo.invalid`; reserved `.invalid` domain indicates synthetic demo data, not a deliverability target or secret.

**Result**
- Eliminated for the audited surfaces.
- Residual note: this was a manual visual spot-check, not OCR across all 14 images. No evidence of secrets/PII found.

## Findings

### Confirmed
1. **Artifact SHA/run identity is correct.**
   The artifact name follows the PR merge SHA, not the branch head SHA. Downloaded artifact, committed catalog, and committed WebPs match exactly.
2. **Builder cleanup is safe for inputs and fail-closed for required portfolio captures.**
3. **WebP integrity is enforced.**
   Catalog digests, file bytes, dimensions, and single-frame status all match the imported media.
4. **Mobile overflow guard exists in both code and capture gate.**
   CSS containment plus runtime overflow polling are both present; targeted tests pass.
5. **Assistant no-query boundary is preserved.**
   Capture contract excludes provider query execution and the inspected screenshots show only the evidence-first initial workspace.
6. **Assistant auth return path is correctly allowlisted without widening trust.**
7. **README gallery coverage and local media paths are complete.**
   All 14 hosted screenshots and 8 contextual visuals resolve locally.
8. **Changed docs keep pre-production boundaries explicit.**
   I found no new text that upgrades these screenshots into production proof.

### Non-blocking gap
1. **Phase 4 social-preview verification evidence is still missing.**
   `plans/260803-2156-portfolio-media-completion/reports/social-preview-verification.md` does not exist, while `docs/deployment-guide.md` still references the older owner-handoff report. Current docs do not falsely claim upload completion, but the acceptance item is still open.

## Supporting Evidence
- GitHub run `30868766788`: success; PR event; head SHA `0c8f3ff710a1907ca375577d0433eed20a4ba526`.
- GitHub artifact API for run `30868766788`: artifact `portfolio-media-2662928a6399a9be8ff9be7a60dfb2ae40b2b03d`, digest `sha256:ccaef8ede664275d0c88aba15425d26d13f92ff5e1496ade581279fbfd9f56c6`.
- GitHub commit API for `2662928a...`: merge commit with second parent `0c8f3ff7...`.
- Post-import CI run `30870554268`: success on `744588884ea232b21071c28163104a220e4525e3`.
- Local focused Python validation: `python -m pytest tests/test_portfolio_media.py -q` -> pass.
- Local focused web validation from `web/`: targeted 24/24 tests pass.
- Local canonical web validation: 396 passed, 9 skipped.

## Recommendations

### Immediate (P1)
- Record social-preview owner verification in `plans/260803-2156-portfolio-media-completion/reports/social-preview-verification.md` once the GitHub Settings upload and public metadata check are actually done.

### Short-term (P1)
- Keep using the canonical web test entrypoints (`npm --prefix web test` or run targeted Vitest from `web/` cwd). Repo-root `web/tests/...` one-file invocation can false-fail alias resolution and should not be mistaken for a product regression.

### Long-term (P2)
- If stricter screenshot-review policy is needed, add OCR-assisted scanning for address-like strings in captured images. Current review is visual plus synthetic demo naming conventions.

## Unresolved Questions
- Has the repository social-preview image been uploaded and publicly verified yet? No evidence file in this branch proves it.

Status: DONE_WITH_CONCERNS
Summary: Requested artifact, media, auth, README, and pre-production-boundary failure modes were eliminated with code, test, and GitHub-run evidence. One non-blocking acceptance gap remains: social-preview verification is still unrecorded.
Concerns/Blockers: No blocker in the audited change set. Keep phase 4 open until social-preview verification evidence exists.
