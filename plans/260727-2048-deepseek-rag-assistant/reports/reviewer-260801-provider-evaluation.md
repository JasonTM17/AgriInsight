## Code Review Summary

### Scope

- Files: corrected protected workflow, hash lock, provider evaluator/workload/CLI, fixture, and focused tests; current working tree reviewed against `origin/main`.
- Focus: re-review of prior C1/H1/H2/H3/H4/M1/M2 findings.
- Scout findings: traced workflow → CLI → workload → `AssistantService` → quota/retriever/client again. The secret step remains isolated from normal CI and uses a no-op telemetry collector; no shared-state race or sensitive aggregate field was found.

### Overall Assessment

No remaining Critical or High code findings in the corrected pending diff. Do not treat this as hosted-provider acceptance: Environment configuration, exact-head normal-CI correlation, provider spend-alert ownership, and telemetry-retention ownership remain external release gates.

### Prior Findings Re-verified

| Finding | Verdict | Evidence |
|---|---|---|
| C1 — unlocked dependency execution before secret use | Resolved | [workflow:25-31](../../../.github/workflows/assistant-provider-evaluation.yml) installs the 11-entry [hash lock](../../../requirements/assistant-provider-evaluation.lock) with `--only-binary`, `--no-deps`, and `--require-hashes`; project source is imported directly, not installed with `.[dev]`. Cache key uses the lock. |
| H1 — arbitrary ref can receive secret | Resolved | [workflow:16](../../../.github/workflows/assistant-provider-evaluation.yml) limits the job to `refs/heads/main` before the environment/secret step. Contract test covers the condition. |
| H2 — substring concept false passes | Resolved | [workload:289-303](../../../src/agriinsight/analytics_api/assistant_provider_evaluation_workload.py) now matches normalized token phrases. Tests reject `4` in `14` and `12` in `120`, while accepting the `6.4`/`6,4` alias. |
| H3 — per-call/output overage schedules later batches | Resolved | [workload:156-180](../../../src/agriinsight/analytics_api/assistant_provider_evaluation_workload.py) cancels the current task group and re-raises; [workload:306-313](../../../src/agriinsight/analytics_api/assistant_provider_evaluation_workload.py) validates both 512 completion tokens and 10,000 total tokens before returning an observation. Test proves only the already-concurrent 1–3 calls may dispatch, with no later batch. |
| H4 — byte-preflight test fails | Resolved | Test now uses three valid, eligible ~4 KB evidence chunks and reaches the byte ceiling before any mock dispatch; the scoped suite passes. |
| M1 — provider latency contains local service work | Resolved | [_LatencyCapturingGenerator:75-101](../../../src/agriinsight/analytics_api/assistant_provider_evaluation_workload.py) starts at generator dispatch and records after the bounded non-streaming client completes; retrieval/quota work is outside this interval. |
| M2 — price snapshot lacks provenance | Resolved | [evaluator:20-24](../../../src/agriinsight/analytics_api/assistant_provider_evaluation.py) emits official source URL and retrieval date; workflow validation and unit test require both fields. |

### Critical Issues

None found.

### High Priority

None found.

### Medium Priority

None found in the corrected diff.

### Low Priority

None. Do not expand scope before hosted gates are evidenced.

### Edge Cases Found by Scout

- Provider usage overage aborts before a later batch. Up to three calls in the active concurrency batch are unavoidable and explicitly bounded/tested.
- Provider errors still produce content-free failure observations and fail the aggregate gate; no automatic retry path appears.
- `ContextVar` gives each concurrent request a unique timing key, while the event-loop-local dispatch map is consumed once per case.
- A local CLI caller can still override `--fixture`; the protected workflow supplies no fixture argument and remains bound to source-controlled `main` content.

### Recommended Actions

1. Merge only after verifying GitHub Environment required reviewers and `main` deployment restrictions out of band.
2. Before recording evidence, prove normal CI and the manual evaluation use the same current `main` SHA.
3. Retain the provider-account spend alert and telemetry-retention ownership as external promotion blockers.

### Metrics

- Focused offline tests: 33 passed, 0 failed (`test_assistant_provider_evaluation*`, retrieval evaluation, workflow contract).
- Syntax: `compileall` passed for evaluator, workload, and CLI.
- Type Coverage: not measured.
- Test Coverage: not measured.
- Linting Issues: not run.
- Live provider/secrets: not invoked or inspected.

### Plan Follow-up

The corrected local/manual tooling meets the reviewed bounded-iteration requirements. Phase 4 remains in progress: no hosted result, protected Environment evidence, exact-head normal-CI correlation, spend-alert ownership, or telemetry-retention ownership was supplied in this re-review.

### Unresolved Questions

- Is the GitHub `assistant-provider-evaluation` Environment configured with required reviewers and a branch restriction matching `main`?
- Which successful normal-CI run and manual-evaluation artifact will establish the same exact current `main` SHA?
- Who owns provider-account spend alerts and production telemetry retention?

Status: DONE_WITH_CONCERNS
Summary: Prior C1/H1/H2/H3/H4/M1/M2 findings are resolved in current code; 33 focused offline tests pass.
Concerns/Blockers: Hosted acceptance remains blocked on out-of-repository Environment/CI/account evidence, not on a remaining Critical/High code finding.
