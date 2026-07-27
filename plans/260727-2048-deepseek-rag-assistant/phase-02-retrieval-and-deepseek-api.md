---
phase: 2
title: Retrieval and DeepSeek API
status: completed
priority: P1
effort: 2d
dependencies:
  - 1
---

# Phase 2: Retrieval and DeepSeek API

## Overview

Create deterministic evidence retrieval from checksum-verified snapshots and a
bounded DeepSeek V4 Flash adapter. Retrieval and generation remain separate,
testable ports.

## Implementation Steps

1. Convert approved scoped aggregates and data-quality definitions into stable
   evidence chunks with source ID, title, timestamp, scope, and checksum.
2. Normalize Vietnamese/English agricultural terms and rank by exact code,
   structured filter, phrase, and token overlap; define deterministic tie-breaks.
3. Reject retrieval results outside the resolved `AuthorizedScope` before they
   can be ranked or serialized.
4. Build the prompt with instructions before evidence, delimit untrusted
   evidence, require evidence IDs in structured JSON, and explicitly allow
   refusal.
5. Call `POST /chat/completions` through `httpx` with bearer auth, V4 Flash,
   thinking disabled for predictable latency, bounded tokens/timeout, JSON
   output, opaque non-PII `user_id`, and no automatic retry for ambiguous
   responses.
6. Validate every cited ID belongs to the supplied evidence; reject malformed,
   empty, uncited, or out-of-corpus model output.

## Tests

- Golden retrieval cases for Vietnamese crop, farm, warehouse, cost, freshness,
  and data-quality questions.
- Cross-tenant/scope negative cases and prompt-injection fixtures embedded in
  both question and source text.
- Provider 200/401/429/5xx/timeout/disconnect/invalid JSON/empty content.
- No HTTP call when retrieval yields insufficient evidence.

## Success Criteria

- [ ] Same corpus/query/scope produces the same ordered evidence.
- [ ] Every factual answer sentence maps to returned evidence IDs.
- [ ] Provider receives no bearer token, email, display name, or hidden
      out-of-scope fact.
- [ ] Usage reports cache hit/miss, prompt, completion, and total token counts
      without recording prompt text.

## Rollback

Disable the assistant flag; retrieval artifacts are derived and can be rebuilt.
