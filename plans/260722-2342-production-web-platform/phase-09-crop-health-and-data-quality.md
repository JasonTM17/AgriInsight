---
phase: 9
title: "crop-health-and-data-quality"
status: pending
priority: P1
effort: "3d"
dependencies: [2, 3, 4]
---

# Phase 9: crop-health-and-data-quality

## Overview

Deliver the Crop Health and Data Quality web views over Phase 2 analytics contracts only. This phase owns no analytics API routers, must not imply realtime telemetry or ML, and must keep the Crop AI-demo warning permanently visible.

## Context Links

- Parent scope and dependency chain: `plans/260722-2342-production-web-platform/plan.md:118-147`
- Artifact-backed analytics, gated release wording, and demo-tenant scope: `plans/260722-2342-production-web-platform/plan.md:121-145`
- Existing dashboard areas and current local/internal status: `README.md:77-85`
- Verified big-data corpus and artifact size: `README.md:159-172`
- Verified AI demo evidence boundary and social asset source: `README.md:175-180`

## Requirements

- Functional: render `Crop Health` and `Data Quality` routes from Phase 2 analytics HTTP only; no direct artifact reads in the browser.
- Functional: preserve the exact taxonomy from Phase 2: `dataStatus=current|stale|partial|missing`, `assessmentMethod=rule-based-heuristic`, `severity=none|low|medium|high`.
- Functional: display field/zone evidence, issue counts, recency, and remediation notes from batch Gold/quality outputs only.
- Functional: carry safe provenance fields `runId`, `asOf`, and `generatedAt`; the browser must not synthesize or mutate them.
- Functional: keep the existing Crop Health demo image behind a permanent "AI-generated demo evidence" warning; never present it as measured field truth.
- Functional: expose loading, empty, partial, unavailable, and permission-denied states with real upstream causes.
- Non-functional: Vietnamese-first copy, accessible chart/table alternatives, server-rendered first view, and no fake fallback data.
- Non-functional: no probability, causal, predictive, anomaly-detection, realtime, or ML language in code, copy, tests, or docs.

## Data Flow And Interfaces

1. Browser route -> Next server loader/BFF -> Phase 2 analytics API -> normalized view model -> HTML/JSON.
2. Image panel -> static asset catalog from existing WebPs -> caption + provenance warning -> browser.
3. Failure path -> upstream `dataStatus` or fetch error -> UI surfaces exact degraded-state copy without inventing new status terms.

Interface contract to lock before UI work:

```ts
type DataStatus = "current" | "stale" | "partial" | "missing";
type AssessmentMethod = "rule-based-heuristic";
type Severity = "none" | "low" | "medium" | "high";

interface CropHealthSummaryVm {
  runId: string;
  asOf: string;
  generatedAt: string;
  dataStatus: DataStatus;
  assessmentMethod: AssessmentMethod;
  severity: Severity;
  evidenceRows: number;
  evidenceSignals: string[];
  demoImage: {
    assetKey: string;
    warning: "AI_GENERATED_DEMO_EVIDENCE_PERMANENT";
  };
}

interface DataQualitySummaryVm {
  runId: string;
  asOf: string;
  generatedAt: string;
  dataStatus: DataStatus;
  assessmentMethod: AssessmentMethod;
  severity: Severity;
  failedChecks: number;
  warningChecks: number;
  issueRows: number;
}
```

Rule: if the API cannot emit `runId`, `asOf`, `generatedAt`, `dataStatus`, `assessmentMethod`, and `severity`, stop and fix Phase 2 contracts first. The browser must not invent taxonomy, freshness thresholds, or confidence language.

## File Matrix

| Action | Path | Purpose |
|---|---|---|
| CREATE | `web/src/app/(platform)/crop-health/page.tsx` | route entry |
| CREATE | `web/src/app/(platform)/crop-health/[fieldCode]/page.tsx` | field detail drill-in |
| CREATE | `web/src/app/(platform)/data-quality/page.tsx` | route entry |
| CREATE | `web/src/lib/server/crop-health-view-model.ts` | BFF normalization over Phase 2 contract |
| CREATE | `web/src/lib/server/data-quality-view-model.ts` | BFF normalization over Phase 2 contract |
| CREATE | `web/src/components/crop-health/*` | cards, tables, permanent warning banner |
| CREATE | `web/src/components/data-quality/*` | summary, issue table, empty/error blocks |
| CREATE | `web/tests/contracts/crop-health-phase2-contract.test.ts` | contract fixture tests against Phase 2 payload shape |
| CREATE | `web/tests/contracts/data-quality-phase2-contract.test.ts` | taxonomy preservation tests |
| CREATE | `web/tests/components/crop-health*.test.tsx` | UI RED/GREEN coverage |
| CREATE | `web/tests/components/data-quality*.test.tsx` | UI RED/GREEN coverage |
| CREATE | `web/tests/e2e/crop-health-and-data-quality.spec.ts` | route + permission smoke |
| CREATE | `web/src/content/vi/crop-health.ts` | domain copy without probability/ML/realtime wording |
| CREATE | `web/src/content/vi/data-quality.ts` | domain recovery and evidence copy |

## TDD Plan

### RED

1. Add web contract tests proving `dataStatus`, `assessmentMethod`, `severity`, `runId`, `asOf`, and `generatedAt` are preserved verbatim from Phase 2 payloads.
2. Add route tests proving `partial` and `missing` payloads render explicit degraded states, not generic empty success.
3. Add component tests proving the AI demo image always renders the permanent warning banner and never displays probability, causal, "actual", "live", or "real-time" copy.
4. Add Playwright smoke proving a denied user cannot deep-link to the two protected routes.

### GREEN

1. Implement BFF/server loaders that map Phase 2 contract fields only, without client recomputation.
2. Implement taxonomy badges, evidence panels, and safe provenance display.
3. Implement route shells, evidence tables, and warning states.

### REFACTOR

1. Consolidate shared `dataStatus`/`severity` badges and unavailable-state components with Phase 5-8 patterns.
2. Remove duplicated formatter logic; keep label and timestamp formatting server-owned where possible.
3. Trim query/parse cost for big-data artifacts before Phase 11 benchmark.

## Implementation Steps

1. Lock analytics contracts for the four page-level reads: crop health summary, crop health detail, data quality summary, data quality issue list.
2. Build BFF/server read models with no browser secrets and no client-side derivation of taxonomy, freshness, or heuristics.
3. Build status, severity, provenance, and evidence components that stay descriptive only.
4. Build the two route trees and shared warning/status primitives.
5. Add route, component, and E2E coverage; then run the big-data smoke path so Phase 11 inherits a stable baseline.

## Commands

Focused:

```powershell
npm --prefix web run test -- crop-health data-quality
npm --prefix web run test -- crop-health-phase2-contract data-quality-phase2-contract
npm --prefix web run test:e2e -- crop-health-and-data-quality
```

Broad:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/check-workspace-disk.ps1
npm --prefix web run build
powershell -ExecutionPolicy Bypass -File scripts/run-big-data-demo.ps1
npm --prefix web run test
```

## Acceptance Criteria

- [ ] Both routes are fed only by Phase 2 analytics HTTP and verified batch artifacts.
- [ ] `dataStatus=current|stale|partial|missing`, `assessmentMethod=rule-based-heuristic`, and `severity=none|low|medium|high` appear exactly as Phase 2 output, with no browser inference.
- [ ] `runId`, `asOf`, and `generatedAt` remain display-safe provenance fields and are never recomputed client-side.
- [ ] Crop Health image always shows the AI-generated demo warning and never claims measured truth.
- [ ] `evidenceSignals` stays descriptive only and never implies probability, ML, or causality.
- [ ] No copy, tests, or UI labels mention realtime, live sensors, prediction, or ML.
- [ ] Big-data run still renders both routes without disk-guard breach or unacceptable empty-state regressions.
- [ ] Permission-denied behavior is explicit and consistent with the protected app shell.

## Risks And Rollback

| Risk | Likelihood x Impact | Mitigation | Rollback |
|---|---|---|---|
| Browser mutates Phase 2 taxonomy | High x High | keep `dataStatus`/`assessmentMethod`/`severity` server-owned | remove badge UI until Phase 2 contract is fixed |
| AI image read as factual evidence | Medium x High | permanent warning banner + test copy snapshots | hide image panel, keep text evidence only |
| Big-data view hydration too slow | Medium x Medium | pre-aggregate in API and benchmark early | return summary-only view, defer drill-down |
| Phase 9 drifts into duplicate API ownership | Medium x High | keep all analytics endpoints in Phase 2 only | remove duplicated client/server code and consume Phase 2 contract only |

## Dependencies And Ownership

- Depends on Phase 2 analytics HTTP, Phase 3 BFF foundation, and Phase 4 shell/navigation.
- Owns only Crop Health and Data Quality web route trees, web read models, and their tests.
- Must not create or modify analytics routers that belong to Phase 2.
- Must not edit Overview, Work Operations, Inventory, Cost, Admin, or release workflow files owned by other phases.

## Commit Slices

1. `feat(web): add crop and quality contract consumers`
2. `feat(web): add crop health and data quality views`
3. `test(web): cover crop taxonomy and demo evidence`

## Locked Contract Inputs

- Phase 9 knows only the checked-in Phase 2 HTTP contract and never depends on artifact filenames.
- CamelCase fields and taxonomies are frozen in Phase 2 OpenAPI: `runId`, `asOf`, `generatedAt`, `dataStatus`, `assessmentMethod`, `severity`, and `evidenceSignals`.
