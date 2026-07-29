---
phase: 4
title: "Evaluation, release, and operations"
status: in-progress
priority: P1
effort: 1.5d
dependencies:
  - 3
---

# Phase 4: Evaluation, release, and operations

## Overview

Block production promotion until groundedness, security, cost, latency,
observability, and container delivery are proven on hosted storage. The
release candidate itself is now backed by verified hosted CI and protected
registry evidence.

## Implementation Steps

1. Build a versioned Vietnamese/English evaluation set with answerable,
   ambiguous, out-of-scope, stale-data, adversarial, and unanswerable questions.
2. Measure retrieval recall@k, citation precision, grounded claim rate, refusal
   precision, time-to-first-byte, end-to-end latency, and token cost.
3. Add redacted metrics for provider outcome, latency, token counts, retrieval
   count, refusal reason, and correlation ID; never log question/evidence/answer.
4. Run dependency, secret, prompt-injection, SSRF, authz, load, browser a11y,
   visual, and failure-recovery gates in hosted CI.
5. Update architecture, setup, privacy, incident, cost-budget, key-rotation, and
   rollback documentation.
6. Build and sign immutable images in CI, generate SBOM/provenance, publish only
   after owner approvals, then attach repo screenshots/GIF without sensitive
   traces.

## Current bounded iteration: deterministic mock latency evaluation

This iteration implements the safe part of the open latency gate without
claiming hosted-provider performance. It must not call DeepSeek, load a local
key, alter `.env*`, add a normal-CI secret, or change the enabled-by-default
assistant configuration.

### Scope and file ownership

| Path | Action | Purpose |
|---|---|---|
| `src/agriinsight/analytics_api/assistant_latency_evaluation.py` | Create | Pure, deterministic percentile computation over synthetic telemetry events plus an outcome-summary boundary over strictly validated values. The result must expose aggregate counts/latencies only, never correlation IDs, prompts, evidence, answers, tenant data, or provider credentials. |
| `scripts/run-assistant-latency-evaluation.py` | Create | Explicit mock-only CLI harness using delayed in-memory provider responses. It emits a bounded aggregate JSON summary and no question/evidence/answer or key. |
| `tests/analytics_api/test_assistant_latency_evaluation.py` | Create | Nearest-rank p50/p95, empty/invalid input, outcome aggregation, synthetic-event determinism, and aggregate redaction. |
| `tests/analytics_api/test_assistant_latency_workload.py` | Create | Six-request in-memory `AssistantService` workload proof for answered, local-refusal, and provider-error telemetry outcomes. |
| `tests/analytics_api/test_assistant_latency_cli.py` | Create | Run the actual mock-only CLI and verify one redacted aggregate JSON line, expected counts, and no stderr. |
| `plans/260727-2048-deepseek-rag-assistant/reports/mock-latency-evaluation-2026-07-29.md` | Create | Bounded local evidence and the explicit hosted/provider boundary. |
| `plans/260727-2048-deepseek-rag-assistant/phase-04-evaluation-release-and-operations.md` | Modify | Record only the implemented mock-harness evidence; leave hosted provider latency, semantic groundedness, spend-alert ownership, and production promotion open. |

### Design and acceptance criteria

1. The evaluator accepts explicit `AssistantTelemetryEvent` values supplied by
   the caller, then validates only the allowlisted fields it consumes. It
   rejects an empty collection, a non-integer latency (including `bool`), a
   negative latency, or an outcome outside `answered`,
   `insufficient_evidence`, `rejected`, and `error` with stable validation
   errors rather than inventing a percentile or serializing arbitrary event
   text.
2. Percentiles use the deterministic nearest-rank rule over sorted integer
   milliseconds: rank `ceil(percentile * n) - 1`, clamped to the sample range.
   The output carries sample count, p50, p95, and a sorted aggregate outcome
   count; it contains no individual event fields.
3. The pure evaluator receives fixed synthetic latency events for deterministic
   nearest-rank tests. Separately, the CLI uses an explicit in-memory generator
   with fixed workload delays, exactly six same-tenant requests, and concurrency
   of three. The workload consists of two answered, two insufficient-evidence,
   and two provider-error paths, remaining below the service's default 30 RPM
   and daily reservation quota. It asserts the exact aggregate outcome counts,
   captures real `perf_counter` telemetry in memory, and prints aggregate JSON
   only. Actual elapsed values are observed rather than asserted exactly; the
   CLI is a reproducible workload harness, not a provider benchmark or release
   gate.
4. Focused tests prove no prompt, evidence content, answer, correlation ID,
   tenant ID, provider code, or API key reaches the summary or harness output.
   They also prove concurrency does not corrupt sample counts, and unknown
   outcome text cannot enter aggregate output.
5. The resulting evidence may state deterministic percentile computation over
   synthetic events and a reproducible mock workload only. Hosted p95
   TTFB/completed-response thresholds, production-scale semantic groundedness,
   provider daily-spend ownership, protected secret injection, and image/public
   deployment promotion remain explicitly open.

### Validation and rollback

- Run the focused latency-evaluation test first, then the existing assistant
  observability, service, client, retrieval-evaluation, settings, endpoint, and
  contract tests. Run the mock CLI once and inspect its JSON keys for redaction.
- This is lightweight Python work; do not invoke Docker, browser, Testcontainers,
  big-data generation, or local provider traffic while the C/D heavy-work guard
  is failing.
- Rollback is deletion of the isolated evaluator/harness files. No persisted
  schema, secret, runtime configuration, provider setting, or public API is
  changed by this iteration.

### Implemented mock-only evidence (2026-07-29)

- `assistant_latency_evaluation.py` now validates exact telemetry event/value
  types and the four service outcomes before computing synthetic-event
  nearest-rank p50/p95 and aggregate-only JSON.
- `run-assistant-latency-evaluation.py` executes exactly six same-tenant,
  in-memory requests at concurrency three. It verifies the aggregate is two
  `answered`, two `insufficient_evidence`, and two `error`, then emits one
  redacted JSON object.
- Focused evaluator, workload, CLI, observability, service, client,
  retrieval-evaluation, settings, endpoint, and contract tests passed 55/55.
  See [mock latency evidence](./reports/mock-latency-evaluation-2026-07-29.md).
- This is not hosted-provider or production SLO evidence. The open gates below
  remain unchanged.

## Release gates

### Verified release evidence

- Hosted CI run [30284795208](https://github.com/JasonTM17/AgriInsight/actions/runs/30284795208)
  succeeded with Python, Java, web, dependency/secret scan, the real
  seven-person Keycloak/PostgreSQL/Spring/FastAPI/Next/Chrome topology, and all
  four candidate images.
- Protected v0.2.2 image release
  [30285933144](https://github.com/JasonTM17/AgriInsight/actions/runs/30285933144)
  succeeded with owner approvals, provenance/SBOM, exact-digest scanning,
  explicit pull-by-digest, and non-root/read-only smoke for Python, backend,
  web, and analytics-api.
- GitHub Release [v0.2.2](https://github.com/JasonTM17/AgriInsight/releases/tag/v0.2.2)
  exists.

### Still open

- p95 time-to-first-byte <= 2.5 seconds and p95 completed response <= 12 seconds
  under the accepted hosted load profile.
- Production-scale semantic groundedness for answerable cases remains
  unmeasured.
- Provider-account daily spend alert ownership remains required before
  protected production promotion.

## Rollback

Disable provider and route flags, retain only aggregate metrics, and redeploy the
previous immutable image digest.

## Local checkpoint

- Versioned 15-case Vietnamese/English retrieval evaluation is green:
  recall@5 `1.00`, refusal precision `1.00`, cross-scope leakage `0`.
- Redacted telemetry, dependency audit, secret boundary, contract drift,
  static accessibility review, Python/web tests, production web build, bounded
  queue, tenant rate/token quota, strict sentence citation, and pending-request
  cancellation pass.
- Hosted CI, protected image release, digest parity across Docker Hub/GHCR, and
  the GitHub Release tag are verified for v0.2.2.
- Hosted latency/load measurement, production-scale semantic groundedness, and
  provider-account daily-spend alert ownership remain open. See
  [evaluation report](./reports/evaluation-2026-07-27.md).
