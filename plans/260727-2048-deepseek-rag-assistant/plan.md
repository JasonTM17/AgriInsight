---
title: DeepSeek RAG agricultural assistant
description: >-
  Tenant-safe, evidence-cited agricultural assistant over authorized AgriInsight
  facts using DeepSeek V4 Flash.
status: in-progress
priority: P2
branch: main
tags:
  - rag
  - deepseek
  - analytics
  - web
blockedBy: []
blocks: []
created: '2026-07-27T13:50:05.152Z'
createdBy: ck-cli
source: cli
---

# DeepSeek RAG agricultural assistant

## Overview

Build a real retrieval-augmented assistant, not a generic chat box. Retrieval
must operate only on server-authorized AgriInsight evidence, generation must cite
the retrieved evidence, and the model must refuse when the corpus does not
support an answer. The local key remains in ignored `.env`; source, images,
logs, traces, screenshots, and CI receive placeholders or environment secrets
only.

## Phases

| Phase | Name | Status |
|-------|------|--------|
| 1 | [Contract and threat model](./phase-01-contract-and-threat-model.md) | Completed |
| 2 | [Retrieval and DeepSeek API](./phase-02-retrieval-and-deepseek-api.md) | Completed |
| 3 | [Secure chat API and web UI](./phase-03-secure-chat-api-and-web-ui.md) | Completed |
| 4 | [Evaluation, release, and operations](./phase-04-evaluation-release-and-operations.md) | In Progress |

## Dependencies

- Consume existing Spring scope authorization and checksum-verified analytics
  snapshots; do not create a second identity boundary.
- Realtime Phase 3 may later add read-model evidence, but the first RAG slice
  does not depend on Kafka availability.

## Acceptance criteria

- No secret or bearer token reaches Git, browser storage, model prompts, logs,
  retained test artifacts, or Docker image layers.
- Retrieval is tenant/farm/warehouse scoped before ranking and produces stable,
  bounded evidence IDs.
- Answers cite every factual claim; unsupported questions return an explicit
  insufficient-evidence response.
- Prompt injection in source text cannot grant permissions, call tools, or
  override the system contract.
- Provider timeout, 401, 429, 5xx, malformed JSON, empty content, and user
  cancellation have tested safe behavior.
- Vietnamese agricultural UX is accessible and responsive, and returns a
  fully validated bounded response through the existing tokenless BFF.

## Decision

Start with deterministic lexical + structured retrieval over authorized Gold
facts. DeepSeek's current public API documents chat completion but no embedding
endpoint; inventing vector embeddings would require an undeclared second
provider. Keep a provider-neutral retriever port so pgvector/hybrid embeddings
can be added later without changing the chat contract.

V1 deliberately buffers the provider JSON before returning it to the browser.
This lets the server validate status, usage, citation IDs, and plain-text
content before any model output crosses the trust boundary. Token streaming is
deferred until it can preserve the same validation guarantee.
