---
phase: 11
title: "browser-quality-security-and-performance"
status: pending
priority: P1
effort: "5d"
dependencies: [5, 6, 7, 8, 9, 10]
---

# Phase 11: browser-quality-security-and-performance

## Overview

Block release until the web stack proves browser correctness, accessibility, security, and big-data performance under realistic role and viewport coverage. This phase is a gate, not optional cleanup.

## Context Links

- Phase boundary, quality gate role, and release dependency: `plans/260722-2342-production-web-platform/plan.md:120-147`
- Parallel domain routes completed before this gate: `plans/260722-2342-production-web-platform/plan.md:123`
- Verified big-data workload and disk-guard commands: `README.md:159-172`
- Existing release wording constraints and protected gate facts: `README.md:27-33`, `README.md:48-59`

## Requirements

- Functional: run Playwright role journeys across seven personas total: `Tenant Admin`, `Executive`, `Data Analyst`, `Farm Manager`, `Inventory Manager`, `Field Worker`, and denied `Supplier`.
- Functional: cover the full protected IA, including Overview, Farm Intelligence, Work Operations, Inventory, Cost, Crop Health, Data Quality, and Tenant Administration where permitted.
- Functional: add Vitest/component tests, axe accessibility checks, visual snapshots, and responsive assertions for `375`, `768`, `1024`, and `1440` widths plus landscape.
- Functional: add adversarial security tests for cache leakage, CSRF boundaries, cookie flags, SSR token leaks, secrets in traces/screens, and forbidden deep links.
- Functional: run a big-data benchmark against the verified 1,050,000-fact dataset before Phase 12 image publication.
- Functional: run the seven personas through the real demo OIDC issuer and reconciled Phase 2 operational/artifact bootstrap; mocked sessions alone cannot pass the end-to-end gate.
- Non-functional: local Windows browser, build, and big-data suites run the workspace disk guard first, pause heavy work below warning headroom (C 10 GB/D 25 GB), hard-fail below C 8 GB/D 20 GB, and keep browser/build caches under ignored `D:\AgriInsight\.cache`. Hosted CI uses its ephemeral `runner.temp`, not a fictitious D drive.
- Non-functional: no weakening of auth, caching, or CSP rules just to make tests green.

## Data Flow And Quality Gates

1. Test runner -> real demo OIDC login -> browser -> Next/BFF -> Spring + analytics API over reconciled demo tenant -> traces, screenshots, videos, web vitals, and authz evidence.
2. Benchmark runner -> big-data artifacts -> route fetch/render timing -> recorded budgets for summary and drill-down pages.
3. Security probes -> headers/cookies/HTML/local storage/network traces -> leak assertions.

Role matrix rule:

- Allowed personas:
  - `Tenant Admin`
  - `Executive`
  - `Data Analyst`
  - `Farm Manager`
  - `Inventory Manager`
  - `Field Worker`
- Denied persona: `Supplier`, which must receive `403` or hidden-nav plus `403` on deep-link, never downgraded access.

Expected route evidence (Spring/FastAPI still decides each request):

| Persona | Required allowed journeys | Required denials/partial behavior |
|---|---|---|
| Tenant Admin | all eight areas and admin mutations | none outside intentionally unavailable data |
| Executive | Overview, Farms, Inventory, Costs, Crop Health | Work mutation, Data Quality, Administration |
| Data Analyst | Overview, Farms, Inventory, Costs, Crop Health, Data Quality | Work mutation, Administration |
| Farm Manager | scoped Overview/Farms/Work/Costs; Spring-scoped Inventory may render without unauthorized analytics | tenant-wide analytics, Data Quality, Administration |
| Inventory Manager | warehouse-scoped Inventory only | farm/crop/cost/data-quality/admin journeys |
| Field Worker | scoped Work append/correction only | management, analytics, inventory, cost, admin journeys |
| Supplier | none | every protected route and BFF operation |

Viewport matrix:

- `375x812`
- `768x1024`
- `1024x768`
- `1440x900`
- `812x375` landscape smoke

Performance budget to lock in repo:

```text
CI lab thresholds now:
LCP <= 2.5s at 375, 768, 1024, 1440, and landscape representative runs
INP <= 200ms
CLS <= 0.10

Later production follow-up:
RUM p75 uses the same metrics but is not a Phase 11 blocker
```

## File Matrix

| Action | Path | Purpose |
|---|---|---|
| CREATE | `web/tests/e2e/role-journeys.spec.ts` | seven-role route coverage |
| CREATE | `web/tests/e2e/security-adversarial.spec.ts` | cache, CSRF, leak probes |
| CREATE | `web/tests/e2e/responsive-visual.spec.ts` | viewport + snapshot coverage |
| CREATE | `web/tests/e2e/big-data-benchmark.spec.ts` | route timing on verified corpus |
| CREATE | `web/tests/components/**/*.a11y.test.tsx` | axe checks |
| CREATE | `web/tests/perf/core-web-vitals-budget.ts` | shared thresholds |
| CREATE | `web/tests/perf/collect-web-vitals.ts` | metric collector |
| CREATE | `web/playwright/accessibility-fixtures.ts` | shared fixtures |
| CREATE | `web/playwright/role-fixtures.ts` | seven-persona sign-in/session helpers |
| CREATE | `web/playwright/cache-and-trace-fixtures.ts` | D-cache setup and secret-scrub helpers |
| MODIFY | `web/playwright.config.ts` | viewport projects, traces, retries |
| MODIFY | `web/vitest.config.ts` | component + axe projects |
| CREATE | `.github/workflows/web-quality.yml` | browser gate using ephemeral CI cache/temp and lab perf thresholds |

## TDD Plan

### RED

1. Add role-journey tests against real demo-issuer users and reconciled operational/artifact masters; fail until the exact allowed/denied/partial matrix above is enforced.
2. Add axe/component tests that fail on contrast, label, and heading regressions.
3. Add adversarial tests that fail on token or secret leakage in HTML, storage, logs, headers, screenshots, or trace artifacts.
4. Add benchmark assertions that fail when big-data routes exceed the CI lab thresholds.

### GREEN

1. Fix route guards, component semantics, and cache headers.
2. Fix layout breakpoints, snapshot drift, and landscape overflow.
3. Optimize loader waterfalls, image delivery, D-cache usage, and big-data aggregation paths.

### REFACTOR

1. Deduplicate fixtures and route setup across Playwright suites.
2. Move repeated forbidden-state and session-expiry assertions into helpers.
3. Keep perf budgets in one file so Phase 12 CI and docs reuse the same source of truth.

## Implementation Steps

1. Freeze the seven-persona matrix from Phase 1 and wire reusable session fixtures with the six allowed roles plus denied `Supplier`.
2. Add component, axe, and responsive tests before changing layout/hardening code.
3. Add adversarial security tests for cache, CSRF, cookie flags, and secret leakage from HTML, traces, and screenshots.
4. Run the verified big-data build, guarded demo bootstrap and reconciliation, then benchmark route fetch/render flows.
5. Gate CI on the suite using `runner.temp`; keep local Windows caches on D and do not proceed to Phase 12 until failures are understood and fixed.

## Commands

Focused:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/check-workspace-disk.ps1
$env:NPM_CONFIG_CACHE='D:\\AgriInsight\\.cache\\npm'; $env:PLAYWRIGHT_BROWSERS_PATH='D:\\AgriInsight\\.cache\\playwright'; npm --prefix web run build
$env:NPM_CONFIG_CACHE='D:\\AgriInsight\\.cache\\npm'; $env:PLAYWRIGHT_BROWSERS_PATH='D:\\AgriInsight\\.cache\\playwright'; npm --prefix web run test -- --project component,a11y
$env:NPM_CONFIG_CACHE='D:\\AgriInsight\\.cache\\npm'; $env:PLAYWRIGHT_BROWSERS_PATH='D:\\AgriInsight\\.cache\\playwright'; npm --prefix web run test:e2e -- role-journeys security-adversarial responsive-visual
$env:NPM_CONFIG_CACHE='D:\\AgriInsight\\.cache\\npm'; $env:PLAYWRIGHT_BROWSERS_PATH='D:\\AgriInsight\\.cache\\playwright'; npm --prefix web run test:perf -- big-data-benchmark
```

Broad:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/check-workspace-disk.ps1
powershell -ExecutionPolicy Bypass -File scripts/run-big-data-demo.ps1
powershell -ExecutionPolicy Bypass -File scripts/bootstrap-demo-environment.ps1 -ConfirmDemo
$env:NPM_CONFIG_CACHE='D:\\AgriInsight\\.cache\\npm'; $env:PLAYWRIGHT_BROWSERS_PATH='D:\\AgriInsight\\.cache\\playwright'; npm --prefix web run test
powershell -ExecutionPolicy Bypass -File scripts/run-backend-tests.ps1 verify
```

## Acceptance Criteria

- [ ] Seven-persona Playwright suite proves the exact allowed/denied/partial route matrix, with `Supplier` denied everywhere.
- [ ] Role journeys authenticate through the real demo issuer and reconciliation proves browser operational IDs map to the same canonical codes as analytics artifacts.
- [ ] Vitest/component and axe suites pass for all shared route primitives.
- [ ] Visual and responsive suites pass at `375`, `768`, `1024`, `1440`, and one landscape project.
- [ ] CI lab thresholds hold at all representative widths: `LCP <= 2.5s`, `INP <= 200ms`, `CLS <= 0.10`.
- [ ] No bearer/session secret appears in HTML, `localStorage`, `sessionStorage`, screenshots, traces, or logs.
- [ ] Cache and CSRF assertions pass without relaxing auth or cookie policy.
- [ ] Big-data benchmark passes against the verified 1,050,000-fact corpus after local warning-level headroom is confirmed and local caches stay on D; hosted CI uses ephemeral runner storage.
- [ ] Phase 11 documents that CI uses lab thresholds now and production RUM p75 is a later follow-up, not a hidden gate here.

## Risks And Rollback

| Risk | Likelihood x Impact | Mitigation | Rollback |
|---|---|---|---|
| Flaky visual or role tests hide real regressions | Medium x Medium | deterministic fixtures, traces, one source of seeded auth | quarantine only after root cause write-up |
| Big-data benchmark fails due to loader waterfalls | High x High | server batching and summary-first reads | keep release blocked; do not lower budgets silently |
| Tokens or secrets leak into traces/screens | Medium x High | explicit leak scans in suite and artifact review | disable trace export and fix source before rerun |
| Disk exhaustion breaks browser/build/big-data jobs | Medium x High | disk guard first, D-cache placement, prune artifacts between jobs | split jobs and rerun; never disable guard |

## Dependencies And Ownership

- Depends on all domain routes being feature-complete first.
- Owns quality/perf/security tests, CI quality gate wiring, and shared fixtures only.
- Must not rewrite domain route behavior except where a failing test proves the defect.

## Commit Slices

1. `test(web): add role and accessibility gates`
2. `test(web): add security and responsive suites`
3. `perf(web): add big-data browser budgets`
4. `ci(web): gate quality before release`

## Runner Policy

- CI uses one recorded controlled runner profile for lab CWV; noisy hardware is fixed or replaced, never compensated by weaker metrics.
- Do not modify `scripts/run-big-data-demo.ps1` unless profiling proves the current runner blocks representative benchmarking.
