# DeepSeek RAG evaluation — 2026-07-27

## Outcome

Local implementation accepted. Protected production promotion not accepted.
The assistant is fail-closed by default and the release overlay leaves it
disabled unless runtime secrets explicitly enable it.

## Evaluated scope

- Dataset: `tests/fixtures/assistant-retrieval-evaluation-v1.json`
- Version: `1.0.0`
- Cases: 15 total; 10 answerable Vietnamese/English, 5 unanswerable,
  ambiguous, injection, or cross-tenant
- Retrieval: deterministic lexical/structured, top 5, scope filter before rank
- Provider: DeepSeek V4 Flash adapter, thinking disabled, strict JSON output

## Results

| Gate | Local result | Threshold | Status |
|---|---:|---:|---|
| Retrieval recall@5 | 1.00 | >= 0.90 | Pass |
| Unsupported-question refusal precision | 1.00 | >= 0.95 | Pass |
| Cross-scope retrieved evidence | 0 | 0 | Pass |
| Structural citation validity | 100% of tested responses | 100% | Pass |
| Prompt/evidence/answer in telemetry | 0 | 0 | Pass |
| Web production dependency vulnerabilities | 0 | 0 high+ | Pass |
| Hosted p95 completed response | Not measured | <= 12 s | Open |
| Semantic grounded-claim rate | Not measured at scale | >= 0.98 | Open |

Structural citation validity means the adapter rejects missing, duplicate,
unknown, non-inline, or out-of-corpus citation IDs. It is not a claim that a
small synthetic set proves semantic correctness at production scale.

## Verified failure behavior

- Assistant disabled: route absent (`404`).
- Missing/invalid key, provider/model/base URL/thinking drift: startup fails.
- No retrieval result: local refusal, zero provider calls and zero tokens.
- Provider `401/403/429/5xx`, timeout, network failure, oversized/malformed
  JSON, invalid usage, and invalid citations: typed redacted failure.
- Browser foreign origin, invalid host, missing/mismatched CSRF, expired
  session, Supplier role, client-supplied model/tenant, oversized/malformed
  body, and invalid upstream answer: rejected before disclosure.
- Browser cancellation is combined with the 45-second BFF timeout and
  propagated to the analytics fetch.

## Secret and privacy boundary

The real provider key exists only in ignored local `.env`. Git, examples,
tests, screenshots, telemetry, generated contracts, Dockerfile layers, and
reports contain placeholders only. Browser state is ephemeral; no conversation
is stored in local/session storage or server persistence.

## Open release gates

- Run the canonical real-Keycloak/PostgreSQL/Spring/FastAPI/Next browser gate
  on hosted storage. Local C free space was about 5.4 GiB, below the 8 GiB hard
  floor, so the heavy topology was not started.
- Measure hosted p50/p95 provider and end-to-end latency under an approved load
  profile; non-streaming V1 has no token-level time-to-first-byte metric.
- Add environment-owned daily spend alerts and an incident owner.
- Build, scan, sign, attest, and publish the exact immutable image only through
  the protected registry workflow with reviewer approval.
- Capture sanitized responsive screenshots/GIF after that real browser gate.

## Unresolved questions

- Who owns the provider daily budget and alert receiver?
- Which protected environment approves Docker Hub/GHCR promotion?
- What production retention period applies to aggregate assistant telemetry?
