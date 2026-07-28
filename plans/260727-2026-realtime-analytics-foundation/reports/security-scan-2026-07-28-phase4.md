# Phase 4 security scan — 2026-07-28

## Scope

- Realtime E2E Java, PowerShell runner, CI wiring, tracked configuration, Python and web manifests.
- Static scan only. Local Testcontainers was intentionally not started because D: is WARN.

## Results

| Area | Result | Evidence |
|---|---|---|
| Tracked secrets | PASS | Structured credential patterns and DeepSeek-style key pattern matched zero tracked source files. |
| Dotenv exposure | PASS | `.env` is ignored by `.gitignore`; no dotenv file is tracked. Its contents were not read. |
| Local/test database passwords | Expected | Matches are local role-bootstrap variables and integration-test fixtures, not runtime application credentials. |
| Runtime web dependencies | PASS | `npm audit --omit=dev --json` reports 0 vulnerabilities. |
| Full web dependency graph | FOLLOW-UP | `npm audit --package-lock-only --json` reports 11 high advisories in development-only ESLint/OpenAPI generation tooling. |
| Python dependency audit | Unavailable | `pip-audit` and `uv` are not installed; no package install was performed. |
| OWASP pattern scan | PASS | No XSS sink, command injection, path traversal, eval, insecure TLS override, or sensitive-token logging match in `backend/src`, `web/src`, `src`, or `scripts`. |
| SQL construction review | PASS | Flagged production query concatenation uses fixed column/predicate fragments; request values remain JDBC parameters. |

## Realtime-specific review

- The guarded runner rejects hidden Maven argument/config sources, clears Failsafe XML before execution, rejects test-skipping/output-redirection options, and never deletes Docker resources.
- Testcontainers inspection is label-scoped for container, network, and volume IDs. Pre-existing resources are snapshotted; the runner only fails on new owned resources and does not remove anything.
- The E2E test uses a mock JWT decoder only to supply deterministic verified claims. It still traverses the real Spring Security filter chain, registered `REALTIME_READ` route, tenant transaction aspect, PostgreSQL RLS, Kafka consumer, and summary controller.

## Dependency follow-up

The 11 high advisories are confined to development tools: `eslint`, `eslint-config-next`, its legacy plugins, and `openapi-typescript` through `@redocly/openapi-core`. They are absent from the production image dependency graph. `npm audit fix --dry-run` offers no safe automatic remediation: it proposes incompatible major ESLint changes and an OpenAPI generator downgrade.

No production release claim should treat the full development dependency graph as clean. A separate, compatibility-tested lint/OpenAPI toolchain migration is required to remove those advisory records safely.

## Unresolved questions

- Should the project accept the development-tool-only advisory risk for this learning release, or schedule a dedicated lint/OpenAPI dependency migration before tagging a production release?
