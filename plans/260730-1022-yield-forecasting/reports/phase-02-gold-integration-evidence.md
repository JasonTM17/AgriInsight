# Phase 2 Gold integration evidence

Status: accepted on 2026-08-01.

## Delivered contract

- `gold/yield_forecast.csv` emits one deterministic row per eligible active
  season, with manifest row-count/checksum and byte-stability coverage.
- The artifact keeps target yield as nullable display context, never a forecast
  feature; it reconciles exact farm/field/season/crop, dates, area, and target
  to the active warehouse season set.
- Warehouse construction rejects fact and dimension relationship mismatches.
  Snapshot loading rejects checksum-valid non-finite, timezone-bearing, stale,
  malformed, or unreconciled forecast evidence before any API request serves.
- Existing public API projections remain unchanged.

## Review evidence

- Stage 2 quality review found missing target context, a farm-field relationship
  gap, and unsafe mixed-timezone parsing. All three were fixed with regression
  coverage.
- Stage 3 adversarial review found no remaining actionable production defect.

## Verification evidence

| Gate | Result |
|---|---|
| Local syntax | `python -m py_compile` passed for touched source and tests |
| Local quality | targeted `python -m ruff check` and `git diff --check` passed |
| Hosted Python | GitHub Actions run 30687514585, `Python analytics`: passed; includes full `pytest`, artifact pipeline run, and `compileall` |
| Hosted security | GitHub Actions run 30687514585: passed |
| Hosted web | GitHub Actions run 30687514585: passed |

Local C/D guard was warning/fail during acceptance, so no disk-heavy local
tests or builds were run after the final review fixes. The hosted Python gate
validated the pushed commit instead.

## Commits

- `2695dcc` `feat(forecast): materialize yield forecast gold artifact`
- `30d3592` `feat(api): validate yield forecast snapshots`
- `5ec79a9` `docs(forecast): document yield gold contract`
