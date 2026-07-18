# Controlled Report Export Research

## Executive Recommendation

Use a dedicated export service module that builds a validated `Cost Analysis` bundle from existing Gold tables, then exposes three Streamlit downloads: CSV, XLSX, and PDF.

Ranked choice:

1. Recommended: artifact-backed export service + Streamlit download buttons
2. Simpler but weaker: build bytes directly in `dashboard/app.py`
3. Heaviest: background/batch report pipeline that persists reports before the UI reads them

The recommended path fits the current repo best because the dashboard already consumes Gold contracts, Streamlit already has a download widget, and the pipeline already produces deterministic artifacts. No new analytic logic is needed; only controlled packaging, validation, and presentation.

## What The Repo Already Gives Us

- Gold tables already cover the needed cost views: `executive_summary`, `monthly_financials`, `cost_breakdown`, `farm_performance`, `crop_profitability`, and `risk_alerts`.
- The dashboard already loads Gold CSVs with cached readers.
- The pipeline already writes deterministic artifacts with a manifest and checksums.
- The current dependency set does not include any XLSX or PDF writer, so those must be added before implementation.
- The Streamlit app is the right place for user-triggered download UX, but it should not own formatting logic.

## Recommended Report Contract

### Inputs

Only allow values that are already present in Gold contracts or that can be normalized to them.

Allowed filters:

- `as_of_date`
- `farm_name`
- `crop_name`
- `activity_type`
- `month_from`
- `month_to`
- `top_n`
- `scope` with a fixed enum such as `executive`, `farm`, `crop`, `activity`

Validation rules:

- Reject any key outside the allowlist.
- Reject any value not present in the precomputed Gold domains.
- Reject free-form field selection, SQL-like expressions, regex, and arbitrary sort keys.
- Normalize month inputs to `YYYY-MM`.
- Treat empty or unknown values as invalid, not as wildcards.

### Outputs

Produce one report bundle per validated request:

- CSV: flat, machine-friendly export for the filtered report dataset
- XLSX: workbook with multiple sheets
- PDF: narrative summary report, not a raw data dump

Suggested workbook sheet layout:

- `Summary`
- `Monthly`
- `Cost Breakdown`
- `Farm Performance`
- `Crop Profitability`
- `Risk Alerts`
- `_meta` or `Metadata`

Suggested PDF sections:

- Title page or header block
- Applied filters and run metadata
- Executive cost summary
- Monthly trend
- Top cost drivers
- Risk notes
- Footer with run id, generated timestamp, and page number

### Metadata

Store metadata in the bundle, not in user-entered text:

- `run_id`
- `as_of_date`
- `generated_at`
- normalized filters
- row counts per exported table
- checksum per artifact
- export version

For XLSX, put metadata on a dedicated sheet. For PDF, render it in the header/footer. For CSV, keep a sidecar manifest in the artifact folder if persistence is enabled.

## Secure Filename Rules

Use a sanitized filename derived from fixed labels and normalized filter values only.

Recommended pattern:

`cost-analysis_<as_of_date>_<scope>_<filter-hash>.<ext>`

Rules:

- Lowercase only.
- ASCII only.
- Replace path separators and whitespace with `-`.
- Strip punctuation except `-` and `_`.
- Cap final length to keep Windows and browser compatibility.
- Never embed raw user input directly.
- Add a short hash suffix when the label would otherwise be ambiguous or too long.

This avoids path traversal, reserved character issues, and ugly filenames that break on Windows downloads.

## Row-Limit Behavior

Do not silently truncate.

Recommended behavior:

- Preflight row counts before rendering bytes.
- If the filtered dataset exceeds the hard cap, fail closed with a clear UI message telling the user to narrow filters.
- Use summary PDF generation even when detail exports are rejected.
- Keep the hard cap configurable, but start conservative.

Practical starting limits:

- CSV/XLSX detail export: 25k to 50k rows per report bundle
- PDF detail tables: top-N only, usually 10 to 30 rows
- Any larger request: reject unless the user explicitly narrows scope

Why this is the right trade-off:

- Streamlit stores download data in memory while the user is connected, so unconstrained exports are a memory risk.
- XLSX generation can stay efficient with constant-memory writing, but that does not justify unlimited exports.
- Cost analysis is a decision-support artifact, not a bulk data-extraction endpoint.

## Export Architecture

### Recommended implementation shape

Create a dedicated module, not dashboard-local helper spaghetti.

Likely shape:

- `build_cost_analysis_export_bundle(...)`
- `validate_cost_export_request(...)`
- `sanitize_export_filename(...)`
- `render_cost_analysis_pdf(...)`
- `render_cost_analysis_xlsx(...)`
- `render_cost_analysis_csv(...)`

Behavior:

1. Dashboard gathers filter inputs.
2. Export request is validated against the allowlist and Gold values.
3. The exporter materializes filtered tables from already loaded Gold data.
4. CSV and PDF can be written to memory.
5. XLSX should be written with a temp file and constant-memory mode, then read back to bytes for download.
6. The bundle returns bytes, filenames, MIME types, and metadata.

### Library choice

> Controller decision: the XlsxWriter option below is **rejected for this milestone**. The spreadsheet skill is authoritative and requires `@oai/artifact-tool`; XlsxWriter/openpyxl must not be used. The recommendation is retained only as a researched alternative and not as an implementation instruction.

CSV:

- Use `pandas.DataFrame.to_csv`.
- Keep UTF-8 explicit.
- Open any temp file with `newline=''` if you use the standard `csv` path.

XLSX:

- Rejected alternative: use `xlsxwriter` as the write engine (disallowed by the spreadsheet skill for this task).
- Prefer `constant_memory=True` for bounded memory use.
- Avoid features that constant-memory mode does not support, such as tables and merge ranges over already-written cells.

PDF:

- Use ReportLab, not HTML-to-PDF, for the first version.
- ReportLab gives deterministic layout and direct font control.
- Use Platypus for paragraphs, tables, and page templates.

### Why not HTML-to-PDF first

HTML-to-PDF usually adds a browser/rendering dependency surface that is not justified here.
This repo is already Python-first, and the report is a controlled tabular summary, not a marketing brochure.

## Vietnamese Font And Layout

The PDF must use a Unicode TrueType font, not default Type 1 fonts.

Recommended approach:

- Bundle a Unicode font family under `assets/fonts/`
- Register it with ReportLab `TTFont`
- Use bold and regular faces from the same family
- Set page size explicitly, likely A4 landscape for wide cost tables
- Use modest margins and page footers
- Keep tables simple and avoid cramped multi-line cells

Why:

- ReportLab docs state TrueType fonts work with Unicode/UTF-8 and are not limited to 256 characters.
- This is required for Vietnamese diacritics.
- Default Helvetica-style fonts are not sufficient for a Vietnamese UI/report.

Practical font candidates:

- Noto Sans
- DejaVu Sans

Both are safer than relying on platform fonts.

## Streamlit UX Flow

Recommended UI flow:

1. Put export filters in a `st.form`.
2. Submit the form with a single `Build export` button.
3. Generate the bundle only after submit.
4. Show three `st.download_button` controls outside the form.
5. Keep a short status block that explains row caps and the applied filters.

Important Streamlit constraints:

- `st.download_button` stores data in memory while the user is connected.
- `st.download_button` cannot be placed inside a form.
- If export generation is expensive, use a callable or defer the build until submit.

This means the clean UX is:

- form for filter intent
- buttons for file delivery
- no rerun-heavy state mutation inside the form

## Disk And Resource Controls

Recommended controls:

- Put temporary files under the repo root artifact area, not the system temp folder, so cleanup and inspection stay inside `D:\AgriInsight`.
- Use a dedicated temp subdirectory such as `artifacts/_tmp/report-exports/`.
- Delete temp files immediately after bytes are loaded.
- Enforce file-size caps before handing data to `st.download_button`.
- Cache the loaded Gold tables, not the exported bytes, unless there is a proven performance issue.
- Keep the export bundle pure and deterministic so tests can compare bytes.

Operational detail:

- XLSX generation should close the workbook before bytes are read.
- On Windows, avoid holding open file handles while the UI tries to read the file.

## Validation Strategy

### Unit tests

Add tests for:

- allowlist validation
- filename sanitization
- row-cap rejection
- report metadata population
- deterministic output for the same inputs

### Dashboard tests

Extend Streamlit AppTest coverage for:

- filter selection
- export submit flow
- download button presence and labels
- failure path for over-limit requests

### File-level validation

- CSV: parse back with pandas and confirm headers and row counts
- XLSX: inspect workbook structure and sheet names, ideally with a dev-only reader
- PDF: extract text and confirm key headings, date, and filter metadata

### Regression tests

Test that:

- filenames never contain path separators
- filters outside the allowlist are rejected
- no export is generated when the row cap is exceeded
- the same request on the same run yields the same bytes

## Exact File Impact

Recommended implementation files:

- `dashboard/app.py`
  - Add the cost export control group and the three download buttons
  - Keep UI logic thin
- `src/agriinsight/report_exports.py` or `src/agriinsight/cost_report_export.py`
  - New export service
  - Request validation
  - Filename sanitization
  - CSV/XLSX/PDF rendering
  - Temp file cleanup
- `pyproject.toml`
  - Add runtime dependencies for XLSX and PDF generation
  - Add dev-only readers if tests need workbook/PDF introspection
- `tests/test_dashboard.py`
  - Verify the export UX and button behavior
- `tests/test_report_exports.py`
  - Verify request validation, filenames, limits, and byte outputs

Possible but not required for the recommended first pass:

- `src/agriinsight/metrics.py`
  - Only if you want a dedicated cost-analysis contract object instead of consuming existing Gold tables directly
- `src/agriinsight/pipeline.py`
  - Only if you want persisted export artifacts generated during the pipeline run
- `assets/fonts/`
  - Only if you bundle a Unicode font for PDF rendering, which is the better choice for Vietnamese

Net: the exporter should not require changes to the Bronze/Silver/Warehouse logic.

## Recommendation Ranking

| Rank | Option | Complexity | Operational risk | Maintenance | Fit |
|---|---|---:|---:|---:|---:|
| 1 | Dedicated export service + Streamlit downloads | Medium | Low | Good | Best |
| 2 | In-dashboard in-memory generation | Low | Medium | Fair | Acceptable for a prototype |
| 3 | Separate report pipeline job | High | Low once built | Heavy | Overkill for this repo right now |

## Why The Recommended Option Wins

- It keeps report logic out of the dashboard page file.
- It reuses existing Gold contracts.
- It limits memory and file-size risk.
- It is testable without browser automation.
- It can be expanded later into persisted report artifacts without rewriting the contract.

## Limitations

This research does not cover:

- exact PDF visual style
- the final font license choice
- whether the export should be a single ZIP bundle or three separate downloads
- scheduling or email delivery of exports
- database-backed permissioning

Those are product decisions, not blockers to the export architecture itself.

## Sources

- Streamlit download button docs: https://docs.streamlit.io/develop/api-reference/widgets/st.download_button
- Streamlit app testing docs: https://docs.streamlit.io/develop/concepts/app-testing/cheat-sheet
- Streamlit forms docs: https://docs.streamlit.io/develop/api-reference/execution-flow/st.form
- Pandas `to_csv`: https://pandas.pydata.org/docs/reference/api/pandas.DataFrame.to_csv.html
- Python CSV docs: https://docs.python.org/3/library/csv.html
- XlsxWriter memory/performance: https://xlsxwriter.readthedocs.io/working_with_memory.html
- XlsxWriter workbook options: https://xlsxwriter.readthedocs.io/workbook.html
- ReportLab pdfgen docs: https://docs.reportlab.com/reportlab/userguide/ch2_graphics/
- ReportLab fonts docs: https://docs.reportlab.com/reportlab/userguide/ch3_fonts/
- ReportLab Platypus docs: https://docs.reportlab.com/reportlab/userguide/ch5_platypus/
