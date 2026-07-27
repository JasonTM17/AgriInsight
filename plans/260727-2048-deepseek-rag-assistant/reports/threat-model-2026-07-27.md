# RAG assistant threat model

Date: 2026-07-27  
Scope: Phase 1 contract and provider configuration

## Protected assets

- Tenant, farm, warehouse, cost, workforce, and data-quality facts.
- Spring bearer/session credentials and the DeepSeek API key.
- Scope decisions, evidence lineage, usage budgets, and provider telemetry.
- Integrity of the assistant answer and its evidence citations.

## Trust boundaries

```text
browser --opaque session--> Next BFF --bearer server-side--> analytics API
analytics API --scope lookup--> Spring/PostgreSQL
analytics API --authorized evidence only--> DeepSeek API
DeepSeek output --strict validation--> plain-text UI
```

The model is not an authorization component. It receives no bearer token,
tenant identifier, email, display name, hidden scope, database credential, or
tool capable of expanding access.

## STRIDE findings and controls

| Threat | Failure mode | Required control |
|---|---|---|
| Spoofing | client supplies tenant, role, model, or base URL | request model forbids extra fields; server resolves identity/scope; provider origin/model fixed |
| Tampering | question or source text asks model to ignore rules | evidence is delimited untrusted data; no tools; output and citation IDs validated against supplied corpus |
| Repudiation | abuse cannot be correlated safely | opaque correlation ID and aggregate outcome/latency/token metrics; no prompt logging |
| Information disclosure | cross-tenant retrieval or secret copied to model/browser | scope filter before ranking; key only in server environment/header; no conversation persistence or browser storage |
| Denial of service | huge prompt/history/output or excessive concurrency | fixed character/item/token/timeout/concurrency limits and request-body cap |
| Elevation of privilege | model invents authorized facts or internal link | answer must cite supplied evidence; UI uses plain text and allowlisted internal citation routes |

## Provider failure policy

- `401/403`: non-retryable configuration error.
- `429`, timeout, and `5xx`: typed retryable unavailable states; no automatic
  ambiguous retry inside the adapter.
- redirects, oversized/malformed/empty JSON, unknown/duplicate citation IDs,
  HTML, and inconsistent usage counts: fail closed.
- provider response bodies and transport exception messages are never exposed
  to clients or retained logs.

## Secret policy

- Real key remains only in ignored `D:\AgriInsight\.env`.
- `.env.example` contains `AGRIINSIGHT_LLM_API_KEY=` with no value.
- CI/containers must receive the key from protected environment secrets; never
  build arguments, labels, layers, screenshots, traces, or repository files.
- Rotation is required if a key enters Git, CI logs, retained artifacts, or a
  browser-visible response.

## Verification completed

- Disabled settings work without a key.
- Enabled settings require a bounded header-safe key.
- Fixed DeepSeek origin, V4 Flash model, token, timeout, evidence, and
  concurrency budgets fail closed.
- Request contract rejects client tenant/model/system-role injection and
  unbounded history.
- Answer contract rejects HTML and inconsistent usage accounting.
- Local ignored credential authenticated against `/models`; the value was not
  copied into tests, source, or this report.

## Unresolved questions

- Production daily spend ceiling and on-call alert owner.
- Formal retention/consent decision if conversation persistence is requested
  after v1.
