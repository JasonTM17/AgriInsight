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

Block release until groundedness, security, cost, latency, observability, and
container delivery are proven on hosted storage.

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

## Release gates

- Retrieval recall@5 >= 0.90 on answerable evaluation cases.
- Citation precision and grounded factual claims >= 0.98.
- Cross-scope evidence leakage = 0 and secret/token leakage = 0.
- Unsupported-question refusal precision >= 0.95.
- p95 time-to-first-byte <= 2.5 seconds and p95 completed response <= 12 seconds
  under the accepted hosted load profile.
- Per-request token/output budgets and daily spend alert are enforced.

## Rollback

Disable provider and route flags, retain only aggregate metrics, and redeploy the
previous immutable image digest.

## Local checkpoint

- Versioned 15-case Vietnamese/English retrieval evaluation is green:
  recall@5 `1.00`, refusal precision `1.00`, cross-scope leakage `0`.
- Redacted telemetry, dependency audit, secret boundary, contract drift,
  static accessibility review, Python/web tests, and production web build pass.
- Protected real-OIDC browser capture, hosted latency/load measurement,
  daily-spend alert ownership, signed image publication, and release approval
  remain external gates. See
  [evaluation report](./reports/evaluation-2026-07-27.md).
