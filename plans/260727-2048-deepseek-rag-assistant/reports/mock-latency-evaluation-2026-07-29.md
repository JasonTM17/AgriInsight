# RAG mock latency evaluation — 2026-07-29

## Status

The bounded mock-only latency evaluator is locally accepted. It validates
aggregate telemetry behavior without calling DeepSeek, loading a provider key,
changing assistant runtime configuration, or adding a normal-CI secret.

## Scope proved

- `assistant_latency_evaluation.py` accepts only exact
  `AssistantTelemetryEvent` values, non-negative exact-integer millisecond
  values, and the canonical outcomes `answered`, `insufficient_evidence`,
  `rejected`, and `error`.
- It calculates p50/p95 from synthetic samples with the documented nearest-rank
  rule and emits only `sample_count`, `p50_ms`, `p95_ms`, and aggregate
  `outcome_counts`.
- The mock CLI exercises six same-tenant requests with concurrency three:
  two answered requests, two local insufficient-evidence refusals, and two
  simulated provider errors. It asserts the expected 2/2/2 aggregate and
  prints one JSON line only.
- The CLI test executes the actual script and verifies one redacted aggregate
  line with no stderr. It does not assert elapsed milliseconds, because
  `AssistantService` deliberately uses real `perf_counter()` measurements.

## Verification

```powershell
python -m py_compile src/agriinsight/analytics_api/assistant_latency_evaluation.py `
  scripts/run-assistant-latency-evaluation.py

python -m pytest `
  tests/analytics_api/test_assistant_latency_evaluation.py `
  tests/analytics_api/test_assistant_latency_workload.py `
  tests/analytics_api/test_assistant_latency_cli.py `
  tests/analytics_api/test_assistant_observability.py `
  tests/analytics_api/test_assistant_service.py `
  tests/analytics_api/test_deepseek_assistant_client.py `
  tests/analytics_api/test_assistant_retrieval_evaluation.py `
  tests/analytics_api/test_assistant_settings.py `
  tests/analytics_api/test_assistant_endpoint.py `
  tests/analytics_api/test_assistant_contract.py

python scripts/run-assistant-latency-evaluation.py
```

Result: 55 tests passed. The CLI reported `sample_count: 6` and aggregate
outcomes `answered: 2`, `insufficient_evidence: 2`, and `error: 2`. Its p50/p95
values are intentionally observed metrics, not deterministic benchmark values.

## Boundary

This evidence does not measure hosted DeepSeek latency, time-to-first-byte,
provider spend, production-scale semantic groundedness, or external deployment.
No Docker image was built or pushed. The next real-provider measurement requires
a protected environment secret and a named provider daily-spend alert owner;
those are separate owner-gated Phase 4 actions.

## Rollback

Remove the isolated evaluator, harness, and tests. No database schema, public
HTTP contract, image, secret, environment setting, or persisted telemetry data
is changed by this slice.

## Unresolved questions

- Who owns provider daily-spend alerts and escalation?
- Which protected environment will hold a provider secret for hosted latency
  measurement?
- What retention policy applies to aggregate assistant telemetry?
