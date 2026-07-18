---
title: "Cost Analysis and Controlled Report Export"
description: "Materialize separate operating-cost and procurement Gold contracts, then expose controlled CSV/XLSX/PDF reporting without double-counting."
status: pending
priority: P1
effort: "3d"
branch: "main"
tags: [feature, analytics, reporting, dashboard, critical]
blockedBy: []
blocks: []
created: "2026-07-18"
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
| 1 | [Cost Data Contracts](./phase-01-cost-data-contracts.md) | Pending |
| 2 | [Controlled Report Export](./phase-02-controlled-report-export.md) | Pending |
| 3 | [Dashboard Verification and Documentation](./phase-03-dashboard-verification-and-documentation.md) | Pending |

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
