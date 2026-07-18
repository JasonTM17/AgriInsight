# Code Review Summary

## Scope

- Focus: final Phase 2 pre-landing review; spec, critical, informational, and adversarial passes.
- Files: `.github/workflows/ci.yml`, `Dockerfile`, `README.md`, `pyproject.toml`, `src/agriinsight/pipeline.py`, all `src/agriinsight/cost_report_*.py`, packaged report assets, Phase 2 tests, and the Phase 2 plan/reports.
- Reviewed code/config/test LOC: approximately 3,736, plus documentation and three font/license assets.
- Data flow scouted: Gold frames -> allowlisted request -> mask/filter -> pre-sort limits -> prepared report -> CSV/PDF plus optional Node/artifact-tool XLSX -> bounded in-memory bundle.
- Dependents: tests only in this phase; dashboard integration remains Phase 3.

## Overall Assessment

**PASS. No Critical, High, Medium, or Low finding remains.**

The first pass found four blockers: CSV lacked embedded lineage, wheel/Docker installs omitted report assets, row caps ran after expensive copies/sorts, and XLSX filesystem errors escaped the typed optional-fallback boundary. It also found misleading timestamp semantics and stale validation paths. All findings were fixed and re-reviewed. Current implementation satisfies the Phase 2 functional and security contract.

## Critical Issues

None.

## High Priority

None.

## Medium Priority

None.

## Low Priority

None.

## Edge Cases Found by Scout

- Oversized individual, combined, and empty selections are counted from boolean masks before either filtered frame is materialized; regression guards prohibit pre-cap `.loc` materialization and sorting.
- XLSX temp creation, payload write, output read, builder failure, missing QA, and success paths normalize failures and clean the per-run directory.
- CSV embeds five lineage columns on every row and escapes formula-leading text.
- Packaged defaults resolve fonts, OFL, and builder relative to the installed package rather than repository CWD.
- Formula-injection QA confirmed injected `=SUM(A1:A2)` is stored in XLSX OOXML as a string cell (`t="str"`), not a formula.
- No SQL, DB loop, mutable shared output path, authentication operation, or schema change is introduced in Phase 2.

## Positive Observations

- Risk-relevant controls are covered at the boundaries: request allowlists, manifest lineage validation, pre-sort row limits, formula-safe text, deterministic exports, typed optional-XLSX fallback, package asset smoke tests, and temp cleanup.

## Verification Evidence

- Independent full gate: `30 passed, 2 skipped` in 20.26 s; skips are PDF tests because the system Python lacks optional PDF packages.
- Post-fix focused gate: `26 passed` in 8.94 s across assets, exports, service, XLSX filesystem failures, and pipeline behavior.
- Final row-cap hardening gate: `python -m pytest tests/test_cost_report_exports.py -q` completed 14/14 tests with exit 0 in 10.4 s.
- `python -m compileall -q -f src dashboard tests`: exit 0.
- `node --check src/agriinsight/report-assets/build-cost-report.mjs`: exit 0.
- Wheel: 618,401 bytes; direct ZIP inspection confirms packaged builder, Noto Sans regular/bold, OFL, README, and asset resolver.
- Font/OFL SHA-256 values match the checked-in provenance README.
- Controlled packaged-default run supplied by lead: CSV 41,531 bytes/120 rows with five lineage columns; deterministic PDF 31,539 bytes/3 visually clean pages; XLSX 28,006 bytes with six exact sheets, `MODEL STATUS: PASS`, zero formula-error matches; bundle 101,076 bytes; zero temp children.
- Earlier full visual workbook QA: six non-empty previews and a 150,389-byte workbook; formula-like text remained text.

## Recommended Actions

1. Mark Phase 2 complete; leave Phase 3 dashboard integration pending.
2. Keep the dashboard explicitly local/internal until authorization scope changes.

## Plan Follow-up

- Phase 2 implementation and success criteria appear complete from code and runtime evidence.
- Phase 3 remains pending and owns dashboard wiring.

## Metrics

- Type coverage: not measured; Python compile gate passed.
- Test coverage: percentage not collected; full suite reported 30 passed and 2 expected optional-dependency skips; post-fix focused suite reported 26 passed.
- Linting issues: no linter configured; Python compile and Node syntax gates clean.

## Unresolved Questions

None.

Status: DONE
