# Phase 01 — Big-data profile

## Goal

Add an explicit scale profile without changing the deterministic standard run
or relying on a hand-copied long CLI command.

## Files

- `src/agriinsight/config.py`
- `src/agriinsight/__main__.py`
- `src/agriinsight/pipeline.py`
- `scripts/run-big-data-demo.ps1`
- focused tests under `tests/`

## Contract

`standard` preserves today's defaults. `big-data` resolves to 10 farms, 12
fields per farm, 60 activities per season, 18 materials, 365 sensor days, and
24 readings per day. That yields 1,051,200 nominal sensor readings before the
intentional duplicate/invalid quality fixtures. Explicit sizing flags override
only their corresponding profile value.

The manifest records the selected profile and the fully resolved configuration;
consumers rely on resolved values, never the profile name alone.

## Implementation

1. Add immutable profile definitions and one resolver shared by CLI/tests.
2. Make dimension arguments optional overrides while preserving no-flag output.
3. Add a guarded PowerShell entry point that writes to `artifacts/big-data`.
4. Add tests for standard compatibility, override precedence, validation, and
   the million-row nominal count without allocating the dataset.
5. Run one real big-data pipeline and verify the manifest plus Gold contracts.

## Risks and rollback

- A one-million-row pandas run can be memory-heavy. Keep output on D, inspect
  free space before/after, and stop if the disk guard leaves PASS state.
- The normal test suite must not generate the large profile.
- Rollback is limited to the profile resolver/runner; existing CLI flags remain
  compatible.
