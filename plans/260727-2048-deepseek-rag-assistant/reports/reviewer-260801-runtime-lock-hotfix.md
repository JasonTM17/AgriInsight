## Runtime Lock Hotfix Re-review

### Scope

- Current corrected diff only: runtime lock, provider-evaluation workflow, workflow contract, and the eager `analytics_api` import roots.
- No provider call and no secret access.

### Critical Issues

None found.

### High Priority

None found.

### Verification

- [`analytics_api/__init__.py:3`](../../../src/agriinsight/analytics_api/__init__.py) eagerly imports `app`; [`app.py:18-31`](../../../src/agriinsight/analytics_api/app.py) imports the router set and `SnapshotCache`; [`snapshot_cache.py:8-9`](../../../src/agriinsight/analytics_api/snapshot_cache.py) imports NumPy and pandas.
- [`requirements/assistant-provider-evaluation.lock:4-23`](../../../requirements/assistant-provider-evaluation.lock) now contains the complete reviewed closure: FastAPI/Starlette/Annotated Doc, HTTPX, Pydantic, NumPy, pandas, and the pinned pandas/Pydantic/HTTP transitive packages. All 20 runtime entries are exact-versioned with SHA-256 hashes.
- [workflow:27-44](../../../.github/workflows/assistant-provider-evaluation.yml) installs only that lock with `--only-binary --no-deps --require-hashes`, verifies the exact checkout SHA, then imports `run_provider_evaluation` with `PYTHONPATH=src`. This source-import step has no secret reference and precedes the only `AGRIINSIGHT_LLM_API_KEY` reference at lines 45-48.
- [workflow contract:68-138](../../../tests/test_assistant_provider_evaluation_workflow_contract.py) asserts the exact 20-package set, hash syntax, source-import command, no secret reference in that step, and ordering before the secret-scoped evaluation step.

### Validation

- `python -m pytest tests/test_assistant_provider_evaluation_workflow_contract.py`: 7 passed.
- Static lock-format check: 20/20 entries exact-versioned and SHA-256-pinned.

### Remaining Operational Gates

- The hosted CPython 3.13 source-import step must pass before the protected provider step; it is now explicitly in the workflow.
- Required reviewers/Environment branch policy, exact-head normal-CI correlation, provider spend-alert ownership, and telemetry-retention ownership remain external release evidence.

### Unresolved Questions

None for this corrected runtime-lock diff.

Status: DONE
Summary: The eager analytics-api import closure is hash-locked and source-import validation runs before the only secret reference. No remaining Critical/High finding.
Concerns/Blockers: None in this micro-review; external hosted-release gates remain separate.
