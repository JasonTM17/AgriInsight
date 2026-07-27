# DeepSeek RAG evaluation — 2026-07-27

## Outcome

Local implementation accepted. Protected v0.2.2 release evidence is verified.
The assistant is fail-closed by default and the release overlay leaves it
disabled unless runtime secrets explicitly enable it.

## Evaluated scope

- Dataset: `tests/fixtures/assistant-retrieval-evaluation-v1.json`
- Version: `1.0.0`
- Cases: 15 total; 10 answerable Vietnamese/English, 5 unanswerable,
  ambiguous, injection, or cross-tenant
- Retrieval: deterministic lexical/structured, top 5, scope filter before rank
- Provider: DeepSeek V4 Flash adapter, thinking disabled, strict JSON output
- Release evidence:
  - Full CI run [30284795208](https://github.com/JasonTM17/AgriInsight/actions/runs/30284795208)
  - Protected image release [30285933144](https://github.com/JasonTM17/AgriInsight/actions/runs/30285933144)
  - GitHub Release [v0.2.2](https://github.com/JasonTM17/AgriInsight/releases/tag/v0.2.2)
  - Docker Hub and GHCR resolve to the same immutable release digest

## Results

| Gate | Local result | Threshold | Status |
|---|---:|---:|---|
| Retrieval recall@5 | 1.00 | >= 0.90 | Pass |
| Unsupported-question refusal precision | 1.00 | >= 0.95 | Pass |
| Cross-scope retrieved evidence | 0 | 0 | Pass |
| Structural citation validity | 100% of tested responses | 100% | Pass |
| Prompt/evidence/answer in telemetry | 0 | 0 | Pass |
| Mixed-role Supplier authorization bypass | Rejected | Rejected | Pass |
| Tenant request/token quota | Enforced per process | Enforced | Pass |
| Provider queue wait | 2 s maximum | Bounded | Pass |
| Hardened live provider smoke | Answered, 1 citation, 302 tokens | Valid bounded response | Pass |
| Full CI run | Succeeded | Python, Java, web, dependency/secret scan, real seven-person browser topology, all four candidate images | Pass |
| Protected image release | Succeeded | Owner approvals, provenance/SBOM, exact-digest scan, pull-by-digest, non-root/read-only smoke | Pass |
| GitHub Release tag | Exists | `v0.2.2` published | Pass |
| Web production dependency vulnerabilities | 0 | 0 high+ | Pass |
| Hosted p95 completed response | Not measured | <= 12 s | Open |
| Semantic grounded-claim rate | Not measured at scale | >= 0.98 | Open |

Structural citation validity means the adapter rejects missing, duplicate,
unknown, non-inline, out-of-corpus, or undeclared citation IDs, uncited factual
sentences, and truncated provider completions. It is not a claim that a small
synthetic set proves semantic correctness at production scale.

The live provider smoke used the ignored local key and a single bounded
synthetic farm fact. Its output recorded only status, citation count, and token
count; prompt, evidence, answer, tenant identity, and key were not printed or
retained.

## Verified failure behavior

- Assistant disabled: route absent (`404`).
- Missing/invalid key, provider/model/base URL/thinking drift: startup fails.
- No retrieval result: local refusal, zero provider calls and zero tokens.
- Provider `401/403/429/5xx`, queue/read timeout, network failure,
  oversized/malformed or truncated JSON, invalid usage, and invalid citations:
  typed redacted failure.
- Browser foreign origin, invalid host, missing/mismatched CSRF, expired
  session, Supplier role (including mixed-role identities), client-supplied
  model/tenant, oversized/malformed body, and invalid upstream answer: rejected
  before disclosure.
- Punctuation-only queries refuse locally. Browser cancellation aborts a still
  pending fetch and is combined with the 45-second BFF timeout.
- The process-local tenant guard limits requests per minute and reserves token
  budget before provider work. It prevents concurrent local oversubscription
  but does not replace provider-account or shared-store billing controls.

## Secret and privacy boundary

The real provider key exists only in ignored local `.env`. Git, examples,
tests, screenshots, telemetry, generated contracts, Dockerfile layers, and
reports contain placeholders only. Browser state is ephemeral; no conversation
is stored in local/session storage or server persistence.

## Open release gates

- Measure hosted p50/p95 provider and end-to-end latency under an approved load
  profile; non-streaming V1 has no token-level time-to-first-byte metric.
- Complete production-scale semantic groundedness measurement for answerable
  cases.
- Add provider-account daily spend alerts and an incident owner; the local
  per-process daily token guard is already enforced.
- Define the retention owner and policy for aggregate assistant telemetry.

## Unresolved questions

- Who owns the provider daily budget and alert receiver?
- What production retention period applies to aggregate assistant telemetry?
