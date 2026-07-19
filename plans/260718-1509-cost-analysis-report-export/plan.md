---
title: "Cost Analysis and Controlled Report Export"
description: "Materialize separate operating-cost and procurement Gold contracts, then expose controlled CSV/XLSX/PDF reporting without double-counting."
status: completed
priority: P1
effort: "3d"
branch: "main"
tags: [feature, analytics, reporting, dashboard, critical]
blockedBy: []
blocks: []
created: "2026-07-18"
completed: "2026-07-19"
createdBy: "ck:plan"
source: skill
---

# Cost Analysis and Controlled Report Export

## Overview

This milestone adds a Cost Analysis contract and a controlled report surface to the existing Bronze-to-Gold MVP. It preserves the current public KPI contract while making operating cost, procurement spend, and inventory valuation explicit separate lenses.

## Scope Decision

**HOLD SCOPE:** deliver farm → season → field → activity drill-down, supplier → warehouse → material procurement view, three separate CSV/XLSX/PDF downloads, tests, visual verification, and documentation. COGS allocation, authentication/RBAC, scheduled delivery, email, and warehouse migration remain later milestones.

## Phases

| Phase | Name | Status |
|-------|------|--------|
| 1 | [Cost Data Contracts](./phase-01-cost-data-contracts.md) | Completed |
| 2 | [Controlled Report Export](./phase-02-controlled-report-export.md) | Completed |
| 3 | [Dashboard Verification and Documentation](./phase-03-dashboard-verification-and-documentation.md) | Completed |

**Progress:** 3/3 phases completed. Cost contracts, controlled exports, modular
dashboard, snapshot verification, local security boundary, disk guard, docs and
browser QA are closed with independent review. All report/test/build output stayed
under `artifacts/_tmp` on D; the final C/D guard passed.

## Dependencies

- Existing `main` baseline commits `82da3ac`, `c8af80e`, `faec407`, and `337c522`.
- No schema migration: all new contracts are materialized from existing star-schema facts.
- XLSX uses only the bundled `@oai/artifact-tool`; no `xlsxwriter`/`openpyxl`. The adapter reports an explicit unavailable error when that internal runtime is not provisioned.
- Temporary report files live under `D:\AgriInsight\artifacts\_tmp`; C/D disk guard must pass before large runs.

## Acceptance Criteria

- Pipeline emits deterministic, sorted Gold datasets for operating cost, procurement spend, detail drill-down, and reconciliation; each reconciles to its own source fact and never adds procurement or stock value to P&L cost.
- Export requests accept only an allowlisted filter contract, reject unknown values and invalid months, fail closed above 25,000 detail rows, sanitize filenames, and clean all temporary files.
- CSV and Vietnamese PDF downloads work in the Python runtime; XLSX is generated with `@oai/artifact-tool`, has auditable formulas/checks and native charts, and is structurally/render-verified when the adapter is available.
- Streamlit Cost Analysis renders without exceptions, applies filters before export, exposes separate download controls, and keeps existing pages/KPIs unchanged.
- Focused tests, full pytest, compile/build checks, PDF page renders, workbook sheet renders, and C/D disk audit pass.

## Phase Commit Boundaries

1. `feat(cost): add cost Gold contracts` — Phase 1 code/tests/docs.
2. `feat(reporting): add controlled report exports` — Phase 2 code/assets/tests.
3. `feat(dashboard): add Cost Analysis and verification tooling` — Phase 3 UI/docs/disk guard.

## Red Team Review

### Session — 2026-07-18

**Findings:** 8 (8 accepted, 0 rejected) · **Severity:** 4 High, 4 Medium. Delegated reviewers timed out; controller adjudication is evidence-backed in [`reports/redteam-local-adjudication.md`](./reports/redteam-local-adjudication.md).

| # | Finding | Severity | Disposition | Applied to |
|---|---|---|---|---|
| 1 | XLSX dependency absent from CI | High | Accept | Phase 2/3 |
| 2 | Spreadsheet formula injection | High | Accept | Phase 2 |
| 3 | Per-table cap bypass | High | Accept | Phase 2 |
| 4 | Report files poison manifest checksums | High | Accept | Phase 2/3 |
| 5 | Missing Gold files fail at import | Medium | Accept | Phase 3 |
| 6 | Font/license verification gap | Medium | Accept | Phase 2/3 |
| 7 | No authorization boundary | Medium | Accept/document | Phase 3 |
| 8 | Public caller compatibility | Medium | Accept | Phase 1 |

### Whole-Plan Consistency Sweep

- Files reread: `plan.md`, all three `phase-*.md`, and local adjudication report.
- Decision deltas checked: 8.
- Reconciled stale references: XLSX availability, row/byte caps, manifest placement, font/dependency prerequisites, and dashboard missing-file behavior.
- Unresolved contradictions: 0.

## Validation Log

### Session 1 — 2026-07-18

**Trigger:** CK hard-mode post-red-team verification. **Questions asked:** 0 (interactive AskUserQuestion is unavailable in this desktop mode; fixed user intent and explicit HOLD decision were used).

#### Verification Results

- **Tier:** Standard (Fact Checker + Contract Verifier)
- **Claims checked:** 24
- **Verified:** 22 | **Failed:** 0 | **Unverified:** 2 (new files intentionally marked Create in phases)
- Evidence: `src/agriinsight/metrics.py:17`, `src/agriinsight/pipeline.py:84-86`, `src/agriinsight/sqlite_schema.sql:88-164`, `dashboard/app.py:511-566`, `tests/test_pipeline.py:1-135`, `tests/test_dashboard.py:1-55`, `pyproject.toml:10-36`, `.github/workflows/ci.yml:16-23`.

#### Confirmed Decisions

- Keep operating cost, procurement spend, and inventory valuation separate; no COGS bridge this milestone.
- Use three separate downloads; CSV/PDF are baseline, XLSX is capability-gated on the explicit artifact-tool runtime.
- Keep report files out of the pipeline checksum root; use D-backed temp paths and the C/D guard.

### Whole-Plan Consistency Sweep

- Files reread: `plan.md`, all `phase-*.md`, both research reports, and local red-team report.
- Decision deltas checked: 11; stale XlsxWriter recommendation explicitly marked rejected.
- Unresolved contradictions: 0.

### Phase Closure Evidence

- Phase 2 status closed from reviewer pass and complete runtime evidence.
- Evidence links: [`reviewer`](./reports/reviewer-2026-07-18-controlled-report-export.md), [`tester`](./reports/tester-2026-07-18-controlled-report-export.md), [`docs-manager`](./reports/docs-manager-2026-07-18-controlled-report-export.md).
- Phase 3 closed with [`reviewer`](./reports/reviewer-2026-07-18-cost-dashboard-phase3.md),
  [`tester`](./reports/tester-2026-07-18-cost-dashboard-phase3.md),
  [`docs-manager`](./reports/docs-manager-2026-07-18-cost-dashboard-phase3.md), and
  [`project-manager`](./reports/pm-2026-07-19-phase-03.md) evidence.
