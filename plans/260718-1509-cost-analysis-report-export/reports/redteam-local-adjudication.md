---
title: "Local Red-Team Adjudication"
date: 2026-07-18
plan: 260718-1509-cost-analysis-report-export
status: complete
---

# Local Red-Team Adjudication

The three delegated reviewers timed out without writing reports. A controller-side evidence pass was completed instead; no code was changed during review. Findings without a concrete source citation were excluded.

## Findings

### 1. XLSX dependency is not present in CI — High — Accept

Evidence: `pyproject.toml:16-24` defines only `dashboard` and `dev` extras; `.github/workflows/ci.yml:20-23` installs `.[dev,dashboard]` and runs Python-only checks. The plan now requires a `reports` extra for CSV/PDF, explicit XLSX capability detection, and a separate bundled-runtime QA step.

### 2. Spreadsheet formula injection — High — Accept

Evidence: `src/agriinsight/sqlite_schema.sql:88-101` stores free-text `material_name`/`notes`, and `dashboard/app.py:525-543` loads those values into UI-facing frames. The phase now requires escaping text beginning with `=`, `+`, `-`, or `@` before workbook writes.

### 3. Bundle row cap could be bypassed across tables — High — Accept

Evidence: `src/agriinsight/pipeline.py:28-30` writes each table independently and the existing Gold contract returns multiple frames from `src/agriinsight/metrics.py:17-20`. The phase now applies both per-table and complete-bundle row/byte caps.

### 4. Report files could poison manifest reproducibility — High — Accept

Evidence: `src/agriinsight/pipeline.py:41-51` recursively hashes the pipeline root and `src/agriinsight/pipeline.py:114` stores those checksums. The phase now keeps final downloads in memory and forbids persisted report files under the checksum root.

### 5. Missing Gold files can fail at import time — Medium — Accept

Evidence: `dashboard/app.py:511-523` checks a fixed required tuple before rendering and `dashboard/app.py:525-543` loads fixed paths. The phase now requires enumerating every new contract and one actionable regeneration error.

### 6. Font support/licensing must be verifiable — Medium — Accept

Evidence: `pyproject.toml:11-14` has no PDF dependency or font asset contract, while the dashboard renders Vietnamese labels at `dashboard/app.py:52-149`. The phase now requires the `reports` extra, bundled Noto Sans regular/bold, OFL text, and render/text verification.

### 7. Local dashboard has no authorization boundary — Medium — Accept/document

Evidence: `dashboard/app.py:511-566` serves artifacts directly with no identity or row filter. The scope explicitly labels the feature local/internal and keeps RBAC/row-level authorization out of this milestone.

### 8. Existing public caller set must remain stable — Medium — Accept

Evidence: `src/agriinsight/pipeline.py:12-13` imports `build_gold_datasets`, and `src/agriinsight/pipeline.py:84-86` writes every returned frame. The phase keeps the function name/signature and extends only the returned dictionary; tests must verify existing keys and bytes.

## Unresolved Questions

None for the HOLD scope. Future COGS allocation policy (FIFO/FEFO/average cost) remains intentionally deferred.
