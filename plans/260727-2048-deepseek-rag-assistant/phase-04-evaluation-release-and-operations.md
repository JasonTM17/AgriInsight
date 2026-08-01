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

## Current bounded iteration: protected live-provider evaluation

This iteration adds an explicit, approval-gated DeepSeek evaluation path. It
must consume the provider key only from an ignored local `.env` or a protected
GitHub Environment secret, emit one aggregate JSON document, and keep normal
pull-request/main CI completely secretless.

### Scope and file ownership

| Path | Action | Purpose |
|---|---|---|
| `tests/fixtures/assistant-retrieval-evaluation-v1.json` | Modify | Add versioned `expectedAnswerConcepts` arrays (canonical lower-case phrases) for the existing 10 answerable cases without weakening the five refusal/cross-scope cases. |
| `src/agriinsight/analytics_api/assistant_provider_evaluation.py` | Create | Validate and aggregate case outcomes, buffered completed-response latency, citations, token usage, and a dated V4 Flash price snapshot without retaining per-case content. |
| `src/agriinsight/analytics_api/assistant_provider_evaluation_workload.py` | Create | Load the closed fixture, exercise the real `AssistantService`/retriever/client boundary through a non-logging telemetry collector plus an in-memory provider-latency wrapper, and keep questions, evidence, answers, case IDs, tenant IDs, correlation IDs, provider diagnostics, and credentials in memory only. |
| `scripts/run-assistant-provider-evaluation.py` | Create | Fail-closed CLI that reads only the key from the process environment, constructs validated in-process assistant settings for the harness, and prints exactly one aggregate JSON line on success. |
| `requirements/assistant-provider-evaluation.lock` | Create | Hash-pin the minimal CPython 3.13 runtime imports used before the protected step receives a provider credential; workflow source runs from `src/` without resolving the project dependency ranges. |
| `tests/analytics_api/test_assistant_provider_evaluation.py` | Create | Pure aggregation, validation, percentile, semantic-concept, citation, cost, and redaction tests. |
| `tests/analytics_api/test_assistant_provider_evaluation_workload.py` | Create | Mock-transport integration proof for the exact 15-case service workload, two repetitions, concurrency three, and zero cross-scope/provider calls for refusal cases. |
| `tests/analytics_api/test_assistant_provider_evaluation_cli.py` | Create | Prove missing credentials fail closed without leaking configuration or producing a false aggregate. |
| `tests/test_assistant_provider_evaluation_workflow_contract.py` | Create | Prove the live workflow is manual-only, environment-protected, secret-scoped, aggregate-artifact-only, and absent from normal CI triggers. |
| `.github/workflows/assistant-provider-evaluation.yml` | Create | Manual protected workflow that installs the locked project, runs the aggregate-only harness, enforces gates, and retains the result for seven days. |
| `plans/260727-2048-deepseek-rag-assistant/reports/` | Modify later | Record the accepted local/hosted run IDs and aggregate metrics only after immutable evidence exists. |

### Current code constraints (verified)

- `AssistantService.answer` records telemetry on reject, error, and success, and
  the default `AssistantTelemetry.record` logger currently emits
  `correlation_id`. The evaluation workload must therefore inject a custom
  in-memory collector instead of process logging, otherwise the manual workflow
  would leak request correlation IDs into stdout/stderr
  (`src/agriinsight/analytics_api/assistant_service.py:55`,
  `src/agriinsight/analytics_api/assistant_observability.py:20`).
- `create_app` wires the assistant only when `assistant.enabled` is true, while
  `AssistantSettings` defaults to `enabled=False`. The manual workflow should
  not export an enable flag or alter deployment defaults just to run this
  harness; it should construct validated settings in-process around the single
  injected API key (`src/agriinsight/analytics_api/app.py:49`,
  `src/agriinsight/analytics_api/assistant_settings.py:16`,
  `src/agriinsight/analytics_api/assistant_settings.py:85`).
- `DeepSeekAssistantClient` already disables thinking and buffers the full
  non-streaming response before validation. The evaluation may measure only
  completed-response latency for the provider path; it must not invent or label
  a token-level TTFB metric
  (`src/agriinsight/analytics_api/deepseek_assistant_client.py:51`,
  `src/agriinsight/analytics_api/deepseek_assistant_client.py:194`,
  `src/agriinsight/analytics_api/deepseek_assistant_client.py:197`).
- The current retrieval fixture and gate prove exact retrieval recall, refusal
  precision, and cross-scope isolation only. Semantic answer scoring needs new
  explicit concept fields in the fixture without relaxing the five refusal
  cases (`tests/analytics_api/test_assistant_retrieval_evaluation.py:68`,
  `tests/analytics_api/test_assistant_retrieval_evaluation.py:69`,
  `tests/analytics_api/test_assistant_retrieval_evaluation.py:72`,
  `tests/fixtures/assistant-retrieval-evaluation-v1.json:2`,
  `tests/fixtures/assistant-retrieval-evaluation-v1.json:145`).

### Exact workload and acceptance criteria

1. Reuse the versioned 15-case Vietnamese/English fixture: 10 answerable cases
   and five unanswerable, ambiguous, prompt-injection, or cross-tenant cases.
   Run two repetitions at concurrency three: exactly 30 service requests, 20
   provider requests, and 10 local refusals with no automatic retries. This is
   exactly the existing 30 RPM process limit and uses no browser-supplied
   provider controls.
2. Answerable cases pass only when the buffered provider response is
   `answered`, its citations are drawn only from the expected evidence IDs,
   every canonical concept listed in `expectedAnswerConcepts` is present after
   deterministic normalization, and the existing strict client has already
   validated every factual sentence/citation marker.
   Refusal cases pass only with `insufficient_evidence`, zero citations, zero
   provider tokens, and no provider call.
3. Before provider traffic, serialize every closed-corpus request and prove it
   is at most 8,000 UTF-8 bytes while the evaluation-only output cap is 512
   tokens. Together these stay below the existing 10,000-token reservation even
   under byte-level tokenization. Reject the workload before the first call if
   any request exceeds the bound. Reject any returned per-call usage above
   10,000 tokens, stop scheduling further work, and enforce an aggregate maximum
   of 200,000 provider tokens for the 20 provider cases. The pricing snapshot
   must make the maximum possible run cost explicit; it does not replace
   provider-account alerts.
4. The aggregate must contain the evaluated source SHA,
   sample/provider/refusal counts, provider p50/p95
   completed-response latency measured from generator dispatch until the full
   non-streaming response body is consumed, answer/refusal/error counts,
   closed-corpus concept/citation-ID pass rate, citation precision, refusal precision,
   cache-hit/cache-miss/output tokens, and a dated official V4 Flash pricing
   snapshot in the provider's published currency. If a USD equivalent is
   emitted, it must remain explicitly labeled as derived and carry its own FX
   source/date alongside the pricing snapshot. The aggregate must contain no
   per-case record or sensitive field.
5. The protected evaluation gate requires zero provider errors, answerable
   closed-corpus concept/citation-ID pass rate `1.00` over the 20 provider-backed answerable
   requests, citation precision `1.00`, refusal precision `1.00`, zero
   provider calls for refusal cases, every request `total_tokens <= 10,000`,
   aggregate provider usage `<= 200,000` tokens, and provider p95
   completed response `<= 12,000 ms`. Non-streaming V1 exposes no token-level
   TTFB metric, so the workflow must not invent or label one. This finite
   closed-corpus metric is not open-ended semantic entailment or
   production-scale model accuracy.
6. The workflow is `workflow_dispatch` only, uses the
   `assistant-provider-evaluation` GitHub Environment, receives only
   `AGRIINSIGHT_LLM_API_KEY` as a secret for the evaluation step, keeps all
   other evaluation constants pinned in repository code, uploads only the
   aggregate JSON artifact, and never passes the secret to normal CI, Docker
   builds, PR artifacts, screenshots, logs, or release images.
7. The manual job must reject every ref except `refs/heads/main`, install its
   dedicated runtime lock with `pip --require-hashes --only-binary=:all:` before
   importing the source, and never resolve `pyproject.toml` dependency ranges
   in its secret-scoped process.
8. The workflow must assert `git rev-parse HEAD == GITHUB_SHA` before loading
   the secret and write that exact SHA into the aggregate. Hosted acceptance
   requires a protected-environment required reviewer,
   branch policy for `main`, a successful exact-head normal CI run, and a
   successful manual evaluation run. The current `main` SHA, exact normal-CI
   `headSha`, manual evaluation `headSha`, evaluated workflow checkout SHA, and
   manual evaluation artifact `source_sha` must all match
   before any follow-up evidence commit is accepted. Provider-account
   spend-alert ownership and a production telemetry-retention owner remain
   external promotion gates; repository evidence must not claim either is
   configured without proof.

### Validation and rollback

- Run pure evaluator tests first, then mock workload/CLI/workflow-contract
  tests, the existing assistant suites, and the full Python gate. Review the
  aggregate output with an explicit sensitive-token denylist and fail closed if
  any provider-backed request reports `total_tokens > 10,000`.
- Use the ignored local key for one bounded pre-push run only after the offline
  tests pass. Do not print the key, prompt, evidence, answer, tenant, case ID,
  correlation ID, or provider error body. Do not persist an
  assistant-enabled `.env` toggle for this run.
- Merge the disabled/manual tooling only after normal CI and security review.
  Configure the protected environment secret out of band, then run the
  workflow on exact `main` and record immutable evidence in a follow-up docs
  commit.
- Rollback is deletion/disablement of the manual workflow and evaluation-only
  modules. No database schema, public API, web contract, release image default,
  or assistant runtime flag changes in this iteration.

## Release gates

### Verified release evidence

- Exact-head hosted CI run
  [30697294137](https://github.com/JasonTM17/AgriInsight/actions/runs/30697294137)
  succeeded 10/10 on release commit `616527dcc7f4a03720fb48e617f9310ab9614873`
  with Python, Java, web, dependency/secret scan, the real seven-persona
  Keycloak/PostgreSQL/Spring/FastAPI/Next/Chrome topology, and all four
  candidate images.
- Protected v0.4.0 image release
  [30697808763](https://github.com/JasonTM17/AgriInsight/actions/runs/30697808763)
  succeeded 4/4 with owner approvals, provenance/SBOM, exact-digest scanning,
  explicit pull-by-digest, and non-root/read-only smoke for Python, backend,
  web, and analytics-api.
- GitHub Release
  [v0.4.0](https://github.com/JasonTM17/AgriInsight/releases/tag/v0.4.0)
  exists and was published at `2026-08-01T12:01:05Z`.

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
  the GitHub Release tag are verified for v0.4.0.
- Hosted latency/load measurement, production-scale semantic groundedness, and
  provider-account daily-spend alert ownership remain open. See
  [evaluation report](./reports/evaluation-2026-07-27.md).
