---
title: "Production web and analytics platform"
description: "Plan a Vietnamese-first Next 16 platform, real OIDC demo integration, secured Spring workflows, and verified Gold analytics surface without claiming public production release."
status: pending
priority: P1
effort: 51d
branch: "main"
tags: [feature, frontend, backend, api, auth, infra]
blockedBy: []
blocks: []
created: 2026-07-22
---

# Production web and analytics platform

## Outcome

- Deliver a browser-grade, Vietnamese-first platform with real authorized reads and existing safe operational mutations over the farm data stack.
- Deliver an opt-in, real end-to-end demo path with seven OIDC personas, reconciled operational masters, verified 1.05M-row analytics artifacts, and no UI fixture fallback.
- Replace local/internal Streamlit as the primary operator experience only after web quality gates pass.
- Reach gated container release readiness, not public production launch, while backend external release gate stays open.

## Current State

- No web app exists today.
- Current product surface is Streamlit and local/internal.
- Spring already exposes 86 secured routes that remain the domain source of truth.
- Deterministic OpenAPI does not exist yet, so generated clients are unsafe to lock in now.
- Analytics has verified Gold artifacts but no HTTP read layer.
- The backend compose path has migrations/runtime but no demo identity/master bootstrap aligned to Gold canonical codes; without that boundary a production web UI would render empty or mismatched data.
- Verified analytics footprint is 1.05M fact rows and 388.2 MB.
- Eight generated WebPs now exist, including reviewed Work and Administration visuals; Phase 4 promotes them into the machine-readable catalog and deterministic web sync.
- Field Ledger is the visual/design source and the planned web IA spans 8 areas.
- Realtime, ML, and chatbot work stay deferred.

## Scope Challenge

- Existing code: secured stateless Spring resource-server routes, verified analytics artifacts, manifest/checksum/export logic, Streamlit workflow knowledge, and eight provenance-tracked WebPs.
- Minimum change set: freeze contract/auth behavior and missing Work/Admin reads, add guarded demo identity/master bootstrap plus cross-store reconciliation, add analytics HTTP reads, ship secure Next BFF, implement 8 product areas, containerize web and analytics services.
- Deferred by design: realtime sync, ML recommendations, chatbot, true multi-tenant artifact analytics, persistent offline mutation queues, fake runtime data, broad write-path redesign.
- Complexity: cross-stack, contract-sensitive, more than 8 files, more than 2 services. Selected mode: `HOLD SCOPE`.

## Explicit Data Flows

1. Demo integration flow: credential-free OIDC subject map + environment-only local credentials + verified artifact masters -> guarded transactional demo PostgreSQL bootstrap -> one-to-one canonical code reconciliation -> readiness.
2. Auth flow: browser -> Next 16 BFF confidential client -> OIDC provider Authorization Code + PKCE -> opaque HttpOnly DB session cookie; the BFF injects the server-held bearer token when calling Spring and the browser stores no access or refresh token.
3. Operational read flow: browser -> server components/route handlers -> BFF -> secured Spring routes -> normalized view model -> HTML/JSON.
4. Analytics read flow: browser -> BFF/server component -> internal FastAPI Gold read service; FastAPI verifies the same server-held bearer through Spring `/api/v1/me`, reconciles permitted canonical codes, then reads manifest/checksum-backed artifacts for the configured demo tenant UUID only.
5. Asset flow: eight reviewed first-party sources -> canonical `dashboard/assets/generated/catalog.json` -> deterministic hash-checked build sync -> generated/ignored Next public output.
6. Failure flow: auth, scope, reconciliation, or data failure -> fail-closed service state and explicit UI error/partial state -> never fake data.

## Architecture Decisions

- Use Next 16 App Router on npm and Node 24 as the browser boundary and BFF layer.
- Keep the browser tokenless; only opaque HttpOnly DB session cookies are allowed.
- Run a Phase 1 nonce/refresh/logout/concurrency spike before locking auth implementation.
- Run the spike through a pinned minimal Next 16 app and a real credential-free demo OIDC configuration; a library-only harness is insufficient.
- Prefer Better Auth only if the Phase 1 spike proves stable under concurrency and logout/refresh edge cases.
- Use `openid-client` 6 as the fallback if Better Auth fails contract fit or concurrency expectations.
- Keep web OIDC/session storage in a dedicated PostgreSQL schema and role owned by the web service; Spring remains a stateless resource server and never owns BFF session migrations.
- Treat the local session only as an authentication/token vault. Every authorization decision comes from fresh Spring `/api/v1/me` and bounded resource-scope reads.
- Add an internal FastAPI Gold read service instead of exposing raw artifact files or adding analytics HTTP into the web app.
- Reuse existing manifest/checksum/export machinery; analytics remains read-only and artifact-backed.
- Enforce Spring-derived scopes and a demo-tenant limitation in analytics until broader tenant rules are proven.
- Bootstrap demo identities/masters only through an explicit local-demo guard, keep credentials outside source, and block analytics readiness until Spring/artifact canonical codes reconcile one-to-one.
- Keep Vietnamese-first copy and formatting from Phase 4 onward.
- Ship no fake runtime data. Empty, loading, partial, and error states must be real.

## Rejected Options

- Browser bearer/JWT storage: rejected for XSS, refresh drift, and logout inconsistency.
- Direct browser-to-Spring integration: rejected because session normalization, secret handling, and contract shaping belong in the BFF.
- Frontend generation from non-deterministic OpenAPI: rejected until the spec is reproducible.
- Direct artifact download/query from browser: rejected for coupling, provenance leakage, and cache invalidation risk.
- Streamlit/web hybrid runtime for long-term use: rejected because it doubles operational complexity and muddies release criteria.
- Realtime, ML, or chatbot additions now: rejected by YAGNI and absent platform baselines.
- UI fixtures or direct browser mocks as demo runtime data: rejected because they bypass Spring authorization, canonical reconciliation, and the real Big Data path.

## Backwards Compatibility and Migration

- Streamlit remains the internal fallback until Phase 12 exit criteria pass.
- Existing Spring routes remain authoritative; the web layer adapts to them rather than reshaping backend truth.
- No browser token migration exists because browser tokens are disallowed.
- Analytics artifacts remain read-only; this plan adds a read facade, not a new write path.
- Rollout starts with demo tenant analytics and expands only after auth, scope, and quality gates pass.

## Dependency Graph

| Component | Inputs | Outputs | Hard blockers |
|---|---|---|---|
| Browser UI | Vietnamese copy, Field Ledger IA, WebPs | HTML/CSS/JS, user actions | BFF routes, auth cookie, read models |
| Next 16 BFF | deterministic Spring/FastAPI contracts, auth decision | server components, exact route handlers, cookies, normalized JSON | Phases 1-2 contract freeze |
| Spring backend | existing secured routes, JWT resource-server context, Phase 1 read-model additions | operational data, identity/permission context, deterministic OpenAPI | none from this plan |
| Demo integration | verified masters, non-secret subject map, environment credentials | seven personas, operational samples, canonical reconciliation report | Phase 1 contracts, demo-only safety marker |
| FastAPI Gold read | manifest/checksum/export artifacts, demo tenant policy | analytics HTTP reads | verified artifact schema, disk guard |
| Containers/registries | Dockerfiles, CI, registry credentials | `nguyenson1710/agriinsight-web`, `nguyenson1710/agriinsight-analytics-api`, and matching gated GHCR packages | Phase 11 pass, protected release gate |

## Phases

| Phase | Name | Status |
|-------|------|--------|
| 1 | [contract-freeze-and-auth-spike](./phase-01-contract-freeze-and-auth-spike.md) | Pending |
| 2 | [analytics-read-api](./phase-02-analytics-read-api.md) | Pending |
| 3 | [web-foundation-and-secure-bff](./phase-03-web-foundation-and-secure-bff.md) | Pending |
| 4 | [field-ledger-shell-and-design-gates](./phase-04-field-ledger-shell-and-design-gates.md) | Pending |
| 5 | [overview-and-farm-intelligence](./phase-05-overview-and-farm-intelligence.md) | Pending |
| 6 | [work-operations](./phase-06-work-operations.md) | Pending |
| 7 | [inventory-control](./phase-07-inventory-control.md) | Pending |
| 8 | [cost-analysis](./phase-08-cost-analysis.md) | Pending |
| 9 | [crop-health-and-data-quality](./phase-09-crop-health-and-data-quality.md) | Pending |
| 10 | [tenant-administration](./phase-10-tenant-administration.md) | Pending |
| 11 | [browser-quality-security-and-performance](./phase-11-browser-quality-security-and-performance.md) | Pending |
| 12 | [container-release-and-docs](./phase-12-container-release-and-docs.md) | Pending |

## Execution Dependencies And Rollback

| Phase | Entry criteria | Exit artifact | Rollback plan |
|---|---|---|---|
| 1 | none | route/auth contract matrix, missing Work/Admin GET models, deterministic Spring OpenAPI, auth spike report | remove additive reads/spec customizer and discard spike; keep current resource-server behavior untouched |
| 2 | Phase 1 | guarded demo bootstrap/reconciliation plus internal analytics HTTP contract | disable bootstrap/FastAPI; retain verified Streamlit demo and operational DB rollback |
| 3 | Phases 1, 2 | Next app skeleton, web-owned DB session handling, generated clients and secured BFF helpers | disable web entrypoints, retain Streamlit-only operations |
| 4 | Phase 3 | Field Ledger shell, navigation, copy and design gates for 8 areas | revert to minimal shell and hide incomplete areas |
| 5 | Phases 2, 3, 4 | overview and farm intelligence views | remove route exposure; no backend rollback needed |
| 6 | Phases 2, 3, 4 | work operations views | remove route exposure; no backend rollback needed |
| 7 | Phases 2, 3, 4 | inventory control views | remove route exposure; no backend rollback needed |
| 8 | Phases 2, 3, 4 | cost analysis views | remove route exposure; no backend rollback needed |
| 9 | Phases 2, 3, 4 | crop health and data quality views | remove route exposure; no backend rollback needed |
| 10 | Phases 1, 3, 4 | tenant administration reads and existing authorized mutations | hide admin routes and revoke nav exposure |
| 11 | Phases 5-10 | browser, security, performance sign-off | revert failing hardening or perf changes individually |
| 12 | Phase 11; publication additionally requires protected release approval | locally verified versioned images, deployment docs, gated release candidate | do not push to either registry or claim production release while the protected gate is open |

Execution is sequential by default because generated contracts, the route registry, navigation, analytics router registration, and shared workflows are integration points. Phase 2 freezes the analytics spec before Phase 3 generates either client; domain phases may parallelize only after explicit, non-overlapping file ownership is recorded.

## Success Criteria

- Phase 1 freezes the auth and route contract required by the web app and proves the chosen session path under concurrency.
- Explicit demo bootstrap creates real OIDC-linked role personas and Spring masters/samples matching analytic canonical codes; readiness and E2E fail on drift.
- Browser storage contains no bearer token, refresh token, or analytics secret.
- Analytics HTTP serves read-only responses backed by verified 1.05M-row artifacts for the demo tenant.
- All 8 planned product areas are navigable in Vietnamese-first UI with real loading, empty, partial, and error states.
- Eight generated WebPs (six existing plus Work/Admin) ship through the machine-readable catalog with provenance and SHA-256 checks; they provide visual context, never runtime data evidence.
- Web app behavior maps to existing secured Spring routes without inventing fake backend data.
- Streamlit remains available until web quality and deployment gates are met.
- Docker images build locally as `nguyenson1710/agriinsight-web` and `nguyenson1710/agriinsight-analytics-api`; Docker Hub and matching GHCR publication both require the protected gate.
- Plan language and release artifacts avoid claiming public production release before backend gate closure.

## Test Matrix

| Level | What gets tested | Gate |
|---|---|---|
| Unit | cookie/session helpers, auth adapters, BFF mappers, FastAPI artifact readers | required in Phases 1-3 |
| Contract | Spring route request/response assumptions for all web-used endpoints | required before Phase 5 |
| Integration | demo issuer/bootstrap/reconciliation, Next BFF to Spring auth/data, FastAPI to verified artifact path, locale formatting | required before Phase 10 |
| E2E | login, session expiry, logout, unauthorized access, 8-area navigation, analytics demo tenant views | required in Phase 11 |
| Security | no browser token leakage, CSRF/session checks, tenant-scope enforcement, header/cookie policy | required in Phases 1 and 11 |
| Performance | first render, route latency, artifact query latency, image payload size, cold start | required in Phase 11 |
| Release | container build, boot, health, registry push dry run, C/D disk guard before and after heavy work | required in Phase 12 |

## Risk Register

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Better Auth fails nonce/refresh/logout/concurrency needs | High | High | spike in Phase 1, fallback to `openid-client` 6, do not commit auth choice early |
| Contract drift across 86 secured routes with no deterministic OpenAPI | High | High | deterministic checked-in spec, explicit BFF adapters, codegen drift and backend contract tests before feature phases |
| Analytics artifact size and temp files exhaust C or D during build/query work | Medium | High | reuse `scripts/check-workspace-disk.ps1`; pause heavy work below warning thresholds C 10 GB/D 25 GB and fail all guarded work below C 8 GB/D 20 GB; keep temp/cache on D |
| Demo-tenant analytics leaks broader data scope | Medium | High | Spring-derived scope checks, tenant enforcement in FastAPI, authorization tests |
| Operational demo DB and artifacts use unrelated canonical masters | High | High | explicit transactional bootstrap, one-to-one reconciliation, readiness and browser gates |
| Demo bootstrap runs against a non-demo database | Low | Critical | compose marker, explicit confirmation, target allowlist, transaction, fail closed |
| BFF overfetch or serial calls make pages too slow | Medium | Medium | per-page read models, batching, cache-safe GET strategy, perf budget in Phase 11 |
| WebP provenance lost during optimization pipeline | Low | Medium | preserve source metadata/checksum ledger, no silent asset regeneration |
| Vietnamese-first copy gaps create mixed-language UX | Medium | Medium | copy inventory review in Phase 4, locale smoke tests in Phase 11 |
| Release pressure bypasses backend external gate | Medium | High | protected promotion gate, wording control in docs, no production claim until approved |

## Commit Strategy

- Keep one reviewable commit series per phase or parallel phase cluster; do not mix unrelated areas.
- Suggested focused groups: `feat(api): freeze web read contracts`, `test(auth): prove server session flow`, `feat(analytics): add verified read API`, `feat(web): add secure BFF foundation`, then separate conventional commits for each product area, quality gate, and release artifact.
- Tag or annotate the first container-ready commit after Phase 11; Phase 12 promotion remains gated.
- Keep rollback simple: each phase cluster must revert cleanly without touching persisted business data.

## Validation Settings

- Recommended plan gate: `deep/full`.
- Reviewer posture: architecture, auth, data-scope, artifact integrity, release gating.
- Minimum validation questions:
1. Which of the 86 secured Spring routes are mandatory for day-one web parity, and which are intentionally deferred?
2. Does the Phase 1 spike prove nonce, refresh, logout, and concurrent-tab behavior under real DB session storage?
3. What exact artifact schema and checksum rules must FastAPI enforce before returning analytics data?
4. How is demo-tenant scope derived from Spring auth context, and where is cross-tenant leakage prevented?
5. Which exact OIDC issuer/client values, forwarded-host contract, and cookie domain will the protected deployment supply?
6. Which observability backend receives redacted web/BFF/FastAPI logs, metrics, and traces?
7. What objective signal closes the protected backend release gate so Docker Hub and GHCR publication can proceed?

## References

- [phase-01-contract-freeze-and-auth-spike](./phase-01-contract-freeze-and-auth-spike.md)
- [phase-02-analytics-read-api](./phase-02-analytics-read-api.md)
- [phase-03-web-foundation-and-secure-bff](./phase-03-web-foundation-and-secure-bff.md)
- [phase-04-field-ledger-shell-and-design-gates](./phase-04-field-ledger-shell-and-design-gates.md)
- [phase-05-overview-and-farm-intelligence](./phase-05-overview-and-farm-intelligence.md)
- [phase-06-work-operations](./phase-06-work-operations.md)
- [phase-07-inventory-control](./phase-07-inventory-control.md)
- [phase-08-cost-analysis](./phase-08-cost-analysis.md)
- [phase-09-crop-health-and-data-quality](./phase-09-crop-health-and-data-quality.md)
- [phase-10-tenant-administration](./phase-10-tenant-administration.md)
- [phase-11-browser-quality-security-and-performance](./phase-11-browser-quality-security-and-performance.md)
- [phase-12-container-release-and-docs](./phase-12-container-release-and-docs.md)
- `./reports/` for auth spike, contract, performance, security, and release-gate evidence.
- Verified planning facts supplied for this plan: current Streamlit state, 86 secured Spring routes, missing deterministic OpenAPI, missing analytics HTTP, verified 1.05M-row/388.2 MB analytics corpus, eight WebPs with provenance, Field Ledger design source, 8 planned areas, Docker Hub plus GHCR gated release target.

## Unresolved Deployment Inputs

- Docker Hub visibility and protected credentials owner for `nguyenson1710/agriinsight-web` and `nguyenson1710/agriinsight-analytics-api`.
- Exact GHCR owner visibility and whether dual-registry publication is mandatory for every release candidate.
- Public hostname, cookie domain, TLS termination, and `X-Forwarded-*` contract for the BFF.
- Protected backend release gate owner, approval artifact, and closure signal.
- Observability destination for web, BFF, and FastAPI logs/metrics.
- Demo tenant identifier, seed policy, and expansion rule for post-demo tenants.
- Production/staging OIDC issuer, client registration, audience, redirect/logout URIs, and secret owner. Local demo uses a separate pinned upstream issuer with environment-only credentials.

## Validation Log

- 2026-07-22: `HOLD SCOPE` inherited from the user's repeated request for a complete, polished eight-area application with real Big Data and generated imagery. Realtime, ML, chatbot, and fake data remain out of scope.
- 2026-07-22: Verified the repository has no production web package, deterministic Spring OpenAPI, or analytics HTTP service; those are prerequisites, not assumed capabilities.
- 2026-07-22: Rejected Auth.js v5 because the stable registry line is v4 and v5 remains prerelease. Phase 1 evaluates stable Better Auth first and pinned `openid-client` 6 as fallback.
- 2026-07-23: Verified disk policy from project docs: warn at C 10 GB/D 25 GB, fail at C 8 GB/D 20 GB. Latest check: C 14.240 GB, D 28.176 GB after earlier safe npm/pnpm cache cleanup; `tmp/`, artifacts, images, source, and Docker images remain preserved.
- 2026-07-23: Generated, visually reviewed, stripped, and converted the missing Work and Administration visuals to 1440x810 WebP. Both stay below 350 KiB, have pinned SHA-256 values, and pass the visual-asset test suite; Phase 4 now owns catalog promotion/sync rather than image generation.
- 2026-07-23: Completed the Full-tier whole-plan sweep across 13 Markdown files. Applied all 11 evidence-backed red-team findings, found 0 unresolved internal contradictions, and passed `ck plan validate` with 12 phases, 0 errors, and 0 warnings.
- 2026-07-22: User-interview tooling is unavailable in the current execution mode. Deployment-specific values remain explicit protected gates above; no answer is fabricated.

## Red Team Review

### Session — 2026-07-23

The three requested reviewer workers hit an external usage limit, so the controller applied the four Full-tier lenses directly: Fact Checker, Flow Tracer, Scope Auditor, and Contract Verifier. Findings without repository `file:line` evidence were discarded.

**Findings:** 11 (11 accepted, 0 rejected)  
**Severity breakdown:** 1 Critical, 10 High, 0 Medium

| # | Finding | Severity | Disposition | Applied To |
|---|---|---|---|---|
| 1 | No demo identity/master bootstrap reconciled Spring with Gold | Critical | Accept | Phases 2, 11, 12 |
| 2 | New Work GETs could accidentally reuse append-only authorization | High | Accept | Phase 1 |
| 3 | Multi-role analytics scope precedence was undefined | High | Accept | Phase 2 |
| 4 | Auth spike did not prove the real Next 16 integration boundary | High | Accept | Phase 1 |
| 5 | No real OIDC demo path or issuer-specific gate existed | High | Accept | Phases 1, 11, 12 |
| 6 | Web session runtime and migration privileges were not separated | High | Accept | Phases 3, 12 |
| 7 | FastAPI-to-Spring timeout, redirect, payload and correlation controls were missing | High | Accept | Phase 2 |
| 8 | UI-to-Gold mapping handled farm UUID only, leaving other dimensions ambiguous | High | Accept | Phase 5 |
| 9 | HTTP cost export could invoke the eager all-format bundle path | High | Accept | Phase 8 |
| 10 | Root Docker allowlist excludes future web/deploy build context | High | Accept | Phase 12 |
| 11 | Existing release workflow pushes before its exact-digest vulnerability scan | High | Accept | Phase 12 |

Evidence anchors: canonical demo codes originate at `src/agriinsight/synthetic.py:121`, `src/agriinsight/synthetic.py:138`, `src/agriinsight/synthetic.py:155`, and `src/agriinsight/synthetic_inventory.py:83`, while operational contracts expose separate UUID/code pairs at `backend/src/main/java/com/agriinsight/backend/farm/api/FarmResponse.java:8` and `backend/src/main/java/com/agriinsight/backend/farm/api/FarmResponse.java:9`. Work command scope currently requires `ACTIVITY_LOG_APPEND` at `backend/src/main/java/com/agriinsight/backend/operations/application/ActivityLogService.java:74`, while read permission is distinct at `backend/src/main/java/com/agriinsight/backend/authorization/domain/Permission.java:13`. `/me` exposes lists of roles/permissions at `backend/src/main/java/com/agriinsight/backend/identity/api/CurrentUserResponse.java:15`, and role scope differs at `backend/src/main/java/com/agriinsight/backend/authorization/domain/Role.java:29`, `backend/src/main/java/com/agriinsight/backend/authorization/domain/Role.java:32`, and `backend/src/main/java/com/agriinsight/backend/authorization/domain/Role.java:44`. OIDC inputs are mandatory at `backend/src/main/resources/application.yml:61`, `backend/src/main/resources/application.yml:64`, `compose.backend.yaml:93`, and `compose.backend.yaml:95`. The existing runtime/migrator separation is evidenced at `compose.backend.yaml:52`, `compose.backend.yaml:63`, and `compose.backend.yaml:90`. The eager export builds CSV/PDF/XLSX in one call at `src/agriinsight/cost_report_service.py:82`, `src/agriinsight/cost_report_service.py:102`, `src/agriinsight/cost_report_service.py:109`, and `src/agriinsight/cost_report_service.py:111`. Root context starts deny-all at `.dockerignore:1`. Remote push precedes scan at `.github/workflows/publish-images.yml:79`, `.github/workflows/publish-images.yml:86`, and `.github/workflows/publish-images.yml:96`.

### Whole-Plan Consistency Sweep

- Files reread: `plan.md` plus all 12 phase files.
- Decision deltas checked: 11 accepted red-team findings, eight-area IA, exact Spring/FastAPI route families, demo OIDC/bootstrap path, two-registry release gate, and C/D disk policy.
- Stale references reconciled: Work/Admin asset generation is now completed input; Phase 4 owns catalog promotion and deterministic sync.
- Unresolved internal contradictions: 0. Remaining unknowns are protected deployment inputs listed above.
