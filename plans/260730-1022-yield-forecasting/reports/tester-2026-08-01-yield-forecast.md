# Test Report — 2026-08-01 — Phase 3 scoped yield forecast verification

## Test Results Overview
- **Total**: 97 checks (1 typecheck command, 6 Vitest suites, 2 pytest files)
- **Passed**: 7 commands/suites passed | **Failed**: 2 commands/suites failed | **Skipped**: 0
- **Duration**: typecheck ~2s, Vitest ~1.4s, pytest ~9.1s

## Coverage Metrics
- Not generated. No coverage command was run.
- Scope covered by targeted checks: BFF allowlist, upstream client, generated contract types, yield forecast contract parsing, farm intelligence view model, overview route filter contract, analytics API endpoint/openapi tests.

## Failed Tests
### `npm --prefix web run typecheck`
- **Error**: `src/features/farms/components/yield-forecast-panel.tsx(1,8): error TS2300: Duplicate identifier 'Link'.`
- **Cause**: the component imports `Link` twice in the same file.
- **Fix**: remove the duplicate import and re-run typecheck.

### `web/tests/contracts/farm-intelligence.test.ts > keeps farm identity and realized analytics when forecast evidence fails`
- **Error**: `ZodError` from `loadMaster` in `src/features/overview/load-operational-analytics-masters.ts:115`.
- **Cause**: the test fixture for the operational master response no longer satisfies the parser; required fields `active`, `code`, `displayName`, `id`, and `version` are missing.
- **Fix**: update the fixture or the test helper so the mocked master payload matches the current schema.

## Build Status
- **Build**: not run
- **Warnings**: `npx vitest` from repo root hit an npm cache path issue (`D:\npm-cache` invalid); reran successfully from `web/` using the local Vitest binary.
- **Dependencies**: resolved for the executed commands.

## Critical Issues
1. TypeScript compile fails on a duplicate `Link` import in `yield-forecast-panel.tsx`.
2. One farm-intelligence contract test still uses an outdated master-response fixture and fails schema parsing.

## Recommendations
1. Remove the duplicate `Link` import, then rerun `npm --prefix web run typecheck`.
2. Fix the failing farm-intelligence fixture, then rerun the targeted Vitest set.
3. If the root npm cache path keeps breaking ad hoc `npx` runs, pin the cache to a workspace-local directory for future validation commands.

## Unresolved Questions
- Should the farm-intelligence test fixture be updated in the existing helper or replaced with a new shared mock builder?
## Re-run Results — 2026-08-01 15:00 Asia/Bangkok
- **C free**: 11.55 GiB
- **D free**: 20.53 GiB
- `npm --prefix web run typecheck` → PASS
- `web` Vitest targeted set → PASS (6 files, 67 tests)

## Re-run Results — 2026-08-01 15:01 Asia/Bangkok
- **C free**: 11.48 GiB
- **D free**: 20.53 GiB
- `npm --prefix web run typecheck` → PASS
- `web` Vitest targeted set → PASS (6 files, 68 tests)

## Expanded Lightweight Gates — 2026-08-01 15:03 Asia/Bangkok
- **C free**: 11.46 GiB
- **D free**: 20.53 GiB
- `npm --prefix web run contracts:check` → PASS
- `npm --prefix web run lint` → FAIL
  - `web/src/features/overview/overview-filter-schema.ts:44:27` warning promoted to error: `_forecastOffset` assigned but never used (`@typescript-eslint/no-unused-vars`)
- `npm --prefix web test` → PASS (50 files, 402 tests; 9 skipped)

## Lint Re-run — 2026-08-01 15:06 Asia/Bangkok
- **C free**: 11.44 GiB
- **D free**: 20.40 GiB
- `npm --prefix web run lint` → PASS

## Build Gate — 2026-08-01 15:08 Asia/Bangkok
- **C free**: 11.40 GiB
- **D free**: 20.13 GiB
- `npm --prefix web run build` → PASS
