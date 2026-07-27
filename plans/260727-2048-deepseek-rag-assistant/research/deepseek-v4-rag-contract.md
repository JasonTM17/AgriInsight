# DeepSeek V4 RAG contract research

Date: 2026-07-27

## Verified provider facts

- OpenAI-compatible base URL: `https://api.deepseek.com`.
- Current model IDs: `deepseek-v4-flash` and `deepseek-v4-pro`.
- Chat completion supports streaming, JSON output, tool calls, a one-million
  token context, `user_id`, and usage fields including prompt cache hit/miss.
- `deepseek-chat` and `deepseek-reasoner` reached their documented deprecation
  date on 2026-07-24, so new code must use the V4 model IDs.
- The documented API surface found for V4 exposes generation but no embedding
  endpoint. Phase 1 therefore uses deterministic lexical/structured retrieval
  rather than pretending chat vectors are embeddings.
- Local ignored credential passed the read-only `/models` authentication check
  on 2026-07-27. The credential value was not logged or copied into this report.

## Primary sources

- <https://api-docs.deepseek.com/quick_start/pricing-details-usd/>
- <https://api-docs.deepseek.com/api/create-chat-completion>
- <https://api-docs.deepseek.com/quick_start/pricing>
- <https://api-docs.deepseek.com/quick_start/rate_limit>
- <https://api-docs.deepseek.com/guides/json_mode/>
- <https://api-docs.deepseek.com/guides/kv_cache>

## Architecture decision

The simplest viable production slice is retrieval over already authorized,
checksum-verified analytics facts plus DeepSeek V4 Flash generation. It avoids a
new database extension, a second model vendor, and a hidden embedding lifecycle.
The retriever remains a port so a measured pgvector/hybrid upgrade can be added
later if it improves the frozen evaluation set.

## Unresolved questions

- Production daily spend budget and alert owner.
- Whether conversation retention is ever required; v1 intentionally stores none.
