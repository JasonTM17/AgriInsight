# Release Candidate Code Review

Date: 2026-07-27

Verdict: internal candidate LAND; external promotion BLOCKED

## Scope

- Phase 9 Crop Health/Data Quality completion.
- Phase 10 Tenant Administration completion.
- Phase 11 responsive, authorization, security, and performance hardening.
- Phase 12 Dockerfiles, workflows, Compose overlays, helpers, and release docs.

## Assessment

No Critical or High code defect remains. Hosted CI proves the application
contracts, database boundaries, real seven-persona browser behavior, and four
no-push image candidates. Public release remains blocked by explicit owner
controls, not by a hidden implementation fallback.

## Critical issues

None.

## High priority

None.

## Medium release blockers

1. GitHub has no protected `release-images` environment, reviewer, or registry
   secrets. The workflow correctly cannot publish.
2. Docker Hub has no web/analytics repositories. Creating a tag now would fail
   after approval rather than produce a complete four-image release.
3. The repository has no root license file. Candidate OCI labels now omit the
   license rather than claiming MIT; the owner must still choose the legal
   artifact before public promotion.
4. `main` has no branch protection/ruleset. Required CI and review policy must
   be selected and proven before enforcement.

## Boundary review

- Concurrency: publication matrix is serialized; no `latest` or parallel tag
  race exists.
- Error propagation: pre-publish scan/smoke blocks before registry
  authentication; exact-digest failures stop the workflow.
- API contracts: deterministic Spring/FastAPI contracts and generated Next
  types pass drift checks.
- Authorization: fresh Spring identity, BFF allowlists, PostgreSQL RLS,
  Supplier denial, CSRF, trusted host/origin, and opaque sessions pass.
- Input handling: bounded Zod/server validation and idempotency/ETag behavior
  remain intact.
- Data exposure: no bearer/refresh token or raw OIDC subject crosses the
  browser boundary; repository secret/configuration scan is clean.
- Performance: no N+1 release regression found; Big Data routes pass guarded
  render and five-viewport lab Web Vitals budgets.
- Compatibility: image additions and release overlays are opt-in; existing
  Python/backend coordinates remain unchanged.

## Verification

- 202 Python tests.
- 463 Java unit/contract + 100 PostgreSQL integration tests.
- 308 web tests with 11 intentional skips.
- 9 web database privilege tests.
- 26 real Chrome journeys.
- Four image build/Trivy/smoke jobs.
- Contract drift, typecheck, lint, production build, dependency audit, secret
  scan, Compose config, Markdown links, and `git diff --check`.

## Docs impact

Major. README, architecture, deployment, roadmap, codebase summary, phase
status, evidence, and repository-owner handoff are synchronized to the
internal-candidate posture.

## Unresolved questions

- Exact branch policy, release reviewer, token rotation, license, registry
  visibility, and production operations ownership.
