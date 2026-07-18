---
phase: 2
title: "Controlled Report Export"
status: pending
priority: P1
effort: "1.5d"
dependencies: [1]
---

# Phase 2: Controlled Report Export

## Overview

Create a reusable report service that validates filters and emits separate CSV, PDF, and artifact-tool-backed XLSX downloads. Formatting stays outside Streamlit; all outputs carry run metadata and respect resource limits.

## Context Links

- Research: `./research/research-controlled-report-export.md`
- Phase 1 Gold contracts: `./phase-01-cost-data-contracts.md`
- `dashboard/app.py`, `src/agriinsight/config.py`, `pyproject.toml`
- Spreadsheet skill: bundled `style_guidelines.md`, `artifact_tool_docs/API_QUICK_START.md`, `features/charts.md`, `domain_guidance/corporate_finance_fpa.md`
- PDF skill: `pdf/SKILL.md`

## Requirements

- Functional: validate a fixed request allowlist (`scope`, farm/crop/activity/supplier, `month_from`, `month_to`, `top_n`); reject unknown keys, unknown values, malformed months, empty result sets, and detail rows above 25,000.
- Functional: generate UTF-8 CSV, A4-landscape Vietnamese PDF, and workbook sheets `Summary`, `Monthly`, `Cost Detail`, `Procurement Detail`, `Checks`, `Metadata`.
- Non-functional: deterministic filter hash/filename, max output size 10 MB for the complete bundle and 25,000 rows per detail table, temp cleanup under `artifacts/_tmp/report-exports`, no arbitrary filesystem path or SQL input.
- XLSX constraint: author only through `@oai/artifact-tool`; use formula-backed checks/KPI cells, native charts, render/inspect every sheet, and fail clearly if the internal adapter is unavailable.

## Implementation Steps

1. Add immutable request/metadata/bundle dataclasses and allowlist/domain validation; keep activity and procurement lenses separate.
2. Implement CSV and ReportLab PDF renderers with Noto Sans regular/bold plus OFL license, page footer, filters, run id, and top-N tables.
3. Implement the XLSX adapter and `scripts/build-cost-report.mjs`; create a temp JSON payload, invoke Node with an explicit artifact-tool module path, export, inspect formulas, scan errors, render every sheet, and delete temp files. Escape text beginning with `=`, `+`, `-`, or `@` before workbook writes.
4. Enforce per-table and complete-bundle row/byte caps and safe filenames; expose typed `ExportUnavailable`/`ReportValidationError` paths rather than swallowing failures. Keep final downloads in memory; never add report files under the pipeline root that `_artifact_checksums()` scans.
5. Add unit tests for validation, determinism, CSV round-trip, PDF text/page count, cleanup, formula-injection escaping, and adapter-unavailable behavior. Run the artifact-tool workbook QA script on D when bundled dependencies exist.

## Success Criteria

- [ ] Invalid filters, path-like values, and >25,000 rows fail closed with actionable messages.
- [ ] Repeated same-run requests produce identical CSV/PDF bytes and the same safe filename/hash.
- [ ] PDF renders all pages with Vietnamese diacritics, no clipping/overflow, and page numbers.
- [ ] CSV/PDF are always available with the Python `reports` extra; XLSX is shown as available only when the explicit artifact-tool module/runtime is provisioned. When provisioned, it has required sheets, formula-backed checks with `MODEL STATUS: PASS`, no formula errors, native charts, and clean rendered previews.
- [ ] No temp file remains after success or failure; output stays under configured caps.

## Tests / Validation

Run `pytest tests/test_cost_report_exports.py -q`. Use the PDF skill's `pdftoppm` render/visual inspection and the spreadsheet skill's inspect/render/error scan for the XLSX. Run `python -m compileall -q src scripts` and verify the Node builder with the bundled runtime without installing packages.

## Risk Assessment

- **High:** private artifact-tool runtime missing in deploy. Mitigate with an explicit `reports` Python extra for CSV/PDF, capability detection for XLSX, a CI/manual artifact-tool QA step on the bundled runtime, and deployment documentation; never silently label a missing XLSX as ready.
- **High:** PDF font/layout or large in-memory downloads exhaust resources. Mitigate with bundled font, row/byte caps, landscape layout, and temp cleanup.
- **Rollback:** remove exporter module and dashboard buttons; Phase 1 Gold contracts remain usable.

## Security Considerations

Filters are typed/allowlisted; filenames are generated from fixed labels + hash; no raw input becomes a path, formula, SQL fragment, or HTML. Report metadata excludes secrets and personal data. Text written to XLSX is escaped when it could be interpreted as a spreadsheet formula.

## Next Steps

Phase 3 wires the bundle into the dashboard and adds visual/disk verification.
