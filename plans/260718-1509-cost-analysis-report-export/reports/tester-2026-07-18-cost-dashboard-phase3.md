# Phase 3 Test and Browser QA Report

Date: 2026-07-18

Status: PASS

## Scope

- Season-aware export contract and legacy v1 filter-hash compatibility.
- Cost Analysis operating/procurement UI, report submit boundary, snapshot integrity.
- Supplier → warehouse → material identity, duplicate display-name isolation.
- Loopback-only Compose exposure and writable report temp under read-only Gold.
- C/D disk guard and packaging gates.

## Automated Results

| Gate | Result |
|---|---|
| Full `python -m pytest -q -ra` | 65 passed, 3 expected skips, 0 failed; 75.6 s |
| Dashboard + cost-domain focused run | 13 passed |
| Compose security-boundary tests | 2 passed |
| `python -m compileall -q src dashboard tests` | PASS |
| Node builder syntax | PASS |
| `docker compose config --quiet` | PASS |
| `git diff --check` | PASS; only Windows LF/CRLF notices |
| Docs validator | PASS; 5 files checked |

The three skips are the PDF-extra conditional tests at
`tests/test_cost_report_pdf.py:30,55,75`; the current Python environment lacks the
optional PDF extra. Phase 2 contains real PDF/XLSX render evidence.

## Wheel Evidence

- File: `artifacts/_tmp/phase3-landing-wheel/dist/agriinsight-0.2.0-py3-none-any.whl`
- Size: 622,367 bytes
- SHA-256: `1F8599D7616B5A038CCE420371DBAF76618B921DB35664C273FFFFE2A730B1B7`
- Verified contents: procurement dashboard, report contract, JS builder, Noto Sans font.
- Build temp and pip cache stayed under `artifacts/_tmp` on D.

## Browser QA

- Desktop: 1440×1000.
- Narrow: 390×844.
- Operating and procurement tabs rendered; procurement chart/table retained
  supplier, warehouse, material identities and mobile legend stayed below chart.
- KPI values not clipped; forms did not overlap; Vietnamese labels readable.
- Browser console and page-error collections empty.
- Final evidence:
  - `artifacts/_tmp/phase3-browser-final-code/procurement-desktop.png`
  - `artifacts/_tmp/phase3-browser-final-code/procurement-narrow.png`
  - `artifacts/_tmp/phase3-browser-final-code/procurement-narrow-legend.png`
  - `artifacts/_tmp/phase3-browser-final/cost-desktop-stable.png`

## Disk Evidence

| Moment | C free | D free | Result |
|---|---:|---:|---|
| Before landing wheel | 10.217 GB | 28.673 GB | PASS |
| After landing wheel | 10.216 GB | 28.659 GB | PASS |

No cleanup command ran. Test/build/cache/report output stayed below
`D:\AgriInsight\artifacts\_tmp`.

## Unresolved Questions

None for Phase 3. Authentication/RBAC remains an explicit later milestone.
