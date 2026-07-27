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
