---
phase: 3
title: "Dashboard Verification and Documentation"
status: completed
priority: P2
effort: "0.5d"
dependencies: [2]
---

# Phase 3: Dashboard Verification and Documentation

## Overview

Add a modular Cost Analysis page, keep `dashboard/app.py` as the composition shell, and document/verify the complete user flow. Add a safe C/D disk guard for local large runs.

## Context Links

- Phases: `./phase-01-cost-data-contracts.md`, `./phase-02-controlled-report-export.md`
- `dashboard/app.py`, `tests/test_dashboard.py`, `README.md`, `docs/mvp-acceptance.md`
- `.claude/rules/documentation-management.md`

## Requirements

- Functional: page filters farm/crop/season/activity/month and shows operating cost, budget variance, cost/ha, cost/kg, activity drivers, and a separate procurement panel.
- Functional: exports are generated only after a submitted form; buttons are separate and reflect the normalized request; XLSX capability state is explicit. CSV/PDF remain usable when the private XLSX adapter is absent.
- Non-functional: existing five pages and eight Executive metrics remain unchanged; no KPI is recomputed in UI; page is testable with Streamlit AppTest and visually checked in browser.
- Operational: `scripts/check-workspace-disk.ps1` checks C/D free space, warns below 10/25 GB, exits non-zero below 8/20 GB, and never deletes files.

## Implementation Steps

1. Create `dashboard/cost_analysis_page.py` (and a small download-control helper only if needed); move no unrelated page logic.
2. Extend app data loading/required-artifact checks and navigation with Cost Analysis; enumerate every new Gold file and show one actionable regeneration error when any is missing; pass loaded Gold frames to the new page.
3. Add AppTest coverage for navigation, empty/filtered states, row-cap error, and download button labels; run browser visual QA at desktop and narrow width.
4. Add the disk guard script, run it before/after pipeline/export, record C/D free-space readings, and keep generated outputs under D.
5. Update README, data contracts, KPI catalog, architecture, MVP acceptance/backlog, and a reporting/deployment note with `reportlab`/font prerequisites, explicit XLSX adapter provisioning, output-not-in-manifest behavior, and rollback.

## Success Criteria

- [x] Cost Analysis page renders with default artifacts and all filters produce stable tables/metrics.
- [x] AppTest and full pytest pass; compile, Docker compose config, and wheel build pass.
- [x] Browser QA shows no console errors, clipped KPIs, overlapping charts, or unreadable Vietnamese labels.
- [x] Disk guard passes current C/D thresholds; no temp/cache/report output is left outside approved folders.
- [x] Docs match actual file paths, commands, contracts, adapter limitation, and acceptance state.

## Tests / Validation

Run focused AppTest first, then full `pytest`, `python -m compileall -q src dashboard tests`, `docker compose config --quiet`, and `python -m build --wheel` if the build package is available. Render/inspect final PDF/XLSX artifacts and attach concise reports under `plans/260718-1509-cost-analysis-report-export/reports/`.

## Risk Assessment

- **Medium:** the 632-line legacy app remains large. Cost Analysis stays modular; defer broad unrelated refactor to avoid regressions.
- **Medium:** Streamlit reruns can rebuild exports. Use form submit and cached Gold data, not global mutable state.
- **Rollback:** remove Cost Analysis route and disk script; existing pages/artifacts remain valid.

## Security Considerations

No authentication is introduced; clearly label the dashboard as local/internal until row-level authorization exists. Do not expose arbitrary artifact paths or report metadata containing secrets.

## Next Steps

Phase closed with linked reviewer, tester, docs-manager, and PM evidence. Do not
publish the local/internal dashboard until authentication/RBAC is implemented.
