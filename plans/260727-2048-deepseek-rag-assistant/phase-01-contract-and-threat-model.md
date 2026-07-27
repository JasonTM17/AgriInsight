---
phase: 1
title: Contract and threat model
status: completed
priority: P1
effort: 1d
---

# Phase 1: Contract and threat model

## Overview

Freeze an authenticated assistant contract and prove where data, permissions,
prompts, model output, and secrets may flow.

## Implementation Steps

1. Inventory analytics facts and Spring permission/scope contracts usable as
   evidence; exclude raw personal data, audit secrets, bearer credentials, and
   cross-tenant aggregates.
2. Define `AssistantQuery`, bounded conversation context, `EvidenceCitation`,
   answer, refusal, usage, and typed provider failure contracts.
3. Threat-model prompt injection, retrieval poisoning, tenant crossover,
   insecure direct object references, data exfiltration, model hallucination,
   unbounded cost, denial of service, SSRF, and secret leakage.
4. Add environment schema with safe defaults:
   `AGRIINSIGHT_LLM_PROVIDER=deepseek`,
   `AGRIINSIGHT_LLM_BASE_URL=https://api.deepseek.com`,
   `AGRIINSIGHT_LLM_MODEL=deepseek-v4-flash`, and an empty
   `AGRIINSIGHT_LLM_API_KEY` placeholder.
5. Freeze per-request limits for query length, evidence count/bytes, output
   tokens, timeout, conversation turns, and concurrent requests.

## Success Criteria

- [x] Contract rejects unknown fields, empty/oversized questions, client-supplied
      tenant/scope, arbitrary model/base URL, and unbounded history.
- [x] Threat-model tests and review prove scope is resolved server-side before
      retrieval.
- [x] `.env` stays ignored and a repository secret scan finds no key material.
- [x] Provider/model configuration validates fail-closed when assistant is
      enabled.

## Rollback

Assistant remains disabled by default; removing its route and environment flag
restores the current analytics/web behavior without data migration.
