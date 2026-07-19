---
type: reviewer
date: 2026-07-18
scope: phase-3-cost-dashboard
status: pass
---

# Code Review Summary

## Scope

- Files: 34 implementation/test/doc/config files. Dashboard: `dashboard/app.py`, seven new Cost Analysis modules. Domain/export: `cost_report_contract.py`, `cost_report_data.py`, `cost_report_pdf.py`, report builder JS, three new dashboard view-model modules. Operations: `compose.yaml`, `.env.example`, disk guard. Tests: export/PDF/XLSX/AppTest plus four new focused suites. Docs/plan: README, four project docs, reporting guide, plan, Phase 3 file.
- LOC: `+2,430 / -59` before this report; untracked implementation files counted as additions.
- Focus: uncommitted Phase 3 diff, surrounding Phase 1/2 contracts, dependents, local/Docker trust boundary, rerun behavior, optional adapters, plan completion.
- Scout findings: concurrent artifact replacement, host exposure, cache retention, error/path leakage, procurement identity collisions, read-only Docker temp, stale session bundles, empty/missing/corrupt Gold, disk thresholds.

## Overall Assessment

**PASS. No active Critical, High, Medium, or Low finding remains.**

Critical pass checked trust boundaries, artifact races, stale downloads, data-loss behavior, semantic lens separation, validation, compatibility, and disk safety. Informational pass checked performance, state mutation, test realism, documentation, optional adapters, and scope drift. Findings discovered during review were fixed and re-verified on the final tree.

Production-ready within the declared **local/internal** threat model. Authentication, RBAC, and row-level authorization remain explicitly out of scope; network publication must stay disabled until that milestone exists.

## Critical Issues

None.

## High Priority

None.

## Medium Priority

None.

## Low Priority

None.

## Edge Cases Found by Scout

1. **Concurrent pipeline rerun / mixed Gold generation — resolved.** `dashboard/cost_analysis_snapshot.py:38-73` reads manifest A, hashes and parses the same CSV bytes, reads manifest B, and accepts only equal manifests plus matching checksums. `dashboard/cost_analysis_snapshot.py:77-99` retries one transition then fails closed. Tests cover exact-byte parsing, stable mismatch, and manifest transition at `tests/test_cost_analysis_snapshot.py:37-77`.
2. **Unauthenticated host exposure — resolved.** Compose publishes only `127.0.0.1:8501` at `compose.yaml:31-32`; regression guard at `tests/test_local_security_boundaries.py:4-8`. Container-internal `0.0.0.0` remains necessary for the loopback host mapping.
3. **Read-only Gold versus writable XLSX temp — resolved.** `compose.yaml:33-35` keeps `/app/artifacts` read-only and overlays `/app/artifacts/_tmp` writable. Rendered Compose config confirms the parent/child mount modes; regression guard at `tests/test_local_security_boundaries.py:11-15`.
4. **Internal path/runtime leakage — resolved.** Missing files render root-relative names at `dashboard/app.py:537-544`; corrupt snapshots use a fixed UI message at `dashboard/app.py:546-555`; optional XLSX and PDF/runtime details stay in server logs while UI text is stable at `dashboard/cost_analysis_session.py:94-105` and `dashboard/cost_analysis_presenters.py:105-124`. AppTest injects a private Windows path and proves it is absent from UI at `tests/test_dashboard.py:167-186`.
5. **Stale or failed report bundle — resolved.** Submit clears the old bundle before build at `dashboard/cost_analysis_session.py:83-97`. Retrieval requires request equality, verified source fingerprint, run ID, date, and pipeline at `dashboard/cost_analysis_session.py:46-68`. AppTest changes a Cost Gold checksum and confirms downloads disappear at `tests/test_dashboard.py:282-313`.
6. **Procurement display-name collision / incomplete drill-down — resolved.** Drivers group by farm, supplier, warehouse, and material codes plus names at `src/agriinsight/cost_procurement_dashboard.py:31-82`. Presenter retains codes and renders supplier → warehouse → material plus bounded transaction detail at `dashboard/cost_procurement_presenter.py:19-99`. Same-name/different-code regression at `tests/test_cost_dashboard.py:111-156`.
7. **Operating/procurement semantic leakage — not present.** Operating filters and season-context denominators are isolated at `src/agriinsight/cost_operating_dashboard.py:40-143`; procurement reads only `procurement_detail` at `src/agriinsight/cost_procurement_dashboard.py:86-114`. No combined cost measure exists.
8. **Version-1 hash break from optional season — resolved.** Season is allowlisted, domain-validated, scope-validated, canonicalized, and exported at `src/agriinsight/cost_report_contract.py:17-29,58-193`. An unselected season is omitted from the v1 hash payload; fixed regression hash at `tests/test_cost_report_exports.py:44-62`.
9. **Unbounded Streamlit cache — resolved.** Legacy versioned CSV/JSON caches now use `ttl=300` and `max_entries=64` at `dashboard/app.py:43-62`.
10. **Disk boundary and destructive cleanup — safe.** `scripts/check-workspace-disk.ps1:13-23,37-123` validates finite readings and strict thresholds; missing/unreadable drives fail closed. The script has no path input or delete operation. Boundary, missing-drive, test-only, actual-drive, and no-delete checks live at `tests/test_workspace_disk_guard.py:55-213`.

## Positive Observations

- New dashboard code performs vectorized in-memory filtering/grouping only. No DB calls, N+1 path, schema migration, shared global report bundle, or mutation of source Gold frames found.
- AppTest now exercises all five legacy pages, eight Executive metrics, submit-only downloads, both lenses, missing/corrupt/empty Gold, bounded errors, sanitized runtime failure, and stale-bundle invalidation at `tests/test_dashboard.py:71-313`.

## Recommended Actions

1. Lead/project manager: attach this report, sync Phase 3 checkboxes/frontmatter, then close plan status. Reviewer intentionally did not mutate plan state.
2. Keep Compose loopback-only and retain the local/internal banner until authentication, permission checks, and row-level authorization are implemented together.
3. Preserve the snapshot/checksum handshake and business-code grouping in future refactors; both prevent silent data corruption that happy-path UI tests miss.

## Plan Follow-up

- Phase 1/2 contracts and export evidence remain compatible; Phase 2 reviewer/tester reports show no open blocker.
- Phase 3 implementation steps appear complete: modular page, validated forms, separate lenses, controlled downloads, missing/corrupt handling, AppTest, disk guard, Compose boundary, docs, and browser QA evidence.
- `plan.md` and `phase-03-dashboard-verification-and-documentation.md` still say `in-progress`; Phase 3 success checkboxes remain unchecked. Recommend lead update after this reviewer handoff. No plan file edited by reviewer.

## Verification Evidence

- Independent focused final tree: **52 passed, 3 skipped, 0 failed** across export contracts, PDF/XLSX adapters, snapshot, dashboard domain, Streamlit AppTest, Compose security, and disk guard; 55 collected, 55.2 s. Skips are the three declared PDF-extra tests because ReportLab/PyPDF are not installed in the review interpreter.
- Lead landing gate on same final tree: **65 passed, 3 expected PDF-extra skips, 0 failed**; 68 collected, 75.6 s.
- Wheel gate: PASS. Independently re-hashed `agriinsight-0.2.0-py3-none-any.whl`: 622,367 bytes; SHA-256 `1F8599D7616B5A038CCE420371DBAF76618B921DB35664C273FFFFE2A730B1B7`.
- `docker compose -f compose.yaml config`: PASS; rendered host IP `127.0.0.1`, read-only `/app/artifacts`, writable `/app/artifacts/_tmp` overlay.
- `git diff --check`: PASS, zero whitespace errors. Windows LF→CRLF notices only.
- Final disk guard: C `10.218 GB` PASS; D `28.701 GB` PASS; overall exit `0`. No cleanup/deletion performed.
- Lead evidence: compile checks pass; browser QA clean at desktop and 390×844 viewport with no page/console errors or visible clipping.

## Metrics

- Type Coverage: not collected; no project type-coverage gate configured for this review.
- Test Coverage: percentage not collected; focused `52/52` runnable tests passed, full landing suite `65/65` runnable tests passed.
- Linting Issues: not measured by a dedicated linter; `git diff --check` reports 0 errors.
- Build: wheel PASS.
- Active Findings: Critical `0`, High `0`, Medium `0`, Low `0`.

## Unresolved Questions

None.

Status: DONE
Summary: Final Phase 3 tree passes production-readiness review within the documented local/internal boundary.
Concerns/Blockers: None.
