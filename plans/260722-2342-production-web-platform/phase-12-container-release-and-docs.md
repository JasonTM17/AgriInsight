---
phase: 12
title: "container-release-and-docs"
status: pending
priority: P1
effort: "5d"
dependencies: [11]
---

# Phase 12: container-release-and-docs

## Overview

Package the web app and analytics API as non-root deployable images, extend the existing protected image workflow as one serialized four-image publication path, and update release-facing docs plus GitHub repository About metadata. This phase may produce a gated release candidate; it must not claim public production release while the `release-images` environment gate remains open.

## Context Links

- Release gate and registry targets: `plans/260722-2342-production-web-platform/plan.md:121-147`
- Locked image names and no-production-claim rule: `plans/260722-2342-production-web-platform/plan.md:134-135`, `plans/260722-2342-production-web-platform/plan.md:160-166`
- Existing backend registry and protected gate facts: `README.md:27-33`, `README.md:48-59`
- Existing social preview source and local/internal wording: `README.md:85`, `README.md:179-180`

## Requirements

- Functional: build two first-party images only: `nguyenson1710/agriinsight-web` and `nguyenson1710/agriinsight-analytics-api`.
- Functional: publish the same digests to `ghcr.io/jasontm17/agriinsight-web` and `ghcr.io/jasontm17/agriinsight-analytics-api` only after the protected publication gate clears.
- Functional: both images run as non-root with read-only root filesystems except explicit temp/cache directories.
- Functional: build and scan locally/CI independently, then extend `.github/workflows/publish-images.yml` with web/analytics matrix entries and `max-parallel: 1` so Python, backend, web, and analytics publication share the same protected environment, tag derivation, attest/scan/digest rules, and never race.
- Functional: publish semantic and SHA tags only; never push `latest`.
- Functional: generate SBOM, provenance, Trivy scan results, and pull-by-digest smoke evidence for each image.
- Functional: scan a locally loaded candidate before any release tag push, then retain the existing exact-published-digest scan. A failing pre-scan never reaches Docker Hub/GHCR.
- Functional: provide an opt-in end-to-end demo Compose overlay with pinned upstream PostgreSQL and Keycloak, one-shot backend/web migrations, explicit demo bootstrap/reconciliation, pipeline, backend, analytics, and web health ordering. Publish only the two new first-party images; never republish PostgreSQL or Keycloak.
- Functional: update GitHub repository About metadata via `gh repo edit` for description, homepage, and topics, plus README badges/package links, to match the gated-release posture.
- Functional: if GitHub API support is insufficient for social preview updates, produce an owner handoff step instead of pretending it is automated.
- Non-functional: publication must use protected secrets, OIDC where supported, MFA-protected maintainers, and must never hardcode credentials.
- Non-functional: if the protected external publication gate is still open, stop at internal candidate evidence and docs; do not say "production release".

## Release Flow And Interfaces

1. Source tree -> independent web and analytics candidate builds -> pre-publication Trivy scan + non-root/read-only smoke -> approved immutable build -> SBOM/provenance -> remote digest -> exact digest re-scan/smoke.
2. Quality-passed image -> protected publication workflow -> Docker Hub + matching GHCR semantic/SHA tags only.
3. The existing protected workflow publishes its four matrix entries serially; no second tag-triggered release workflow can race it.
4. Gate open -> workflow halts before remote push or marks draft/candidate only.
5. GitHub repository About + README/docs -> same release status string as workflows so product copy cannot outrun operations reality.

Publication contract:

```text
Allowed tags: vX.Y.Z, sha-<gitsha>
Forbidden tag: latest
Required evidence: SBOM, provenance attestation, Trivy zero HIGH/CRITICAL, digest smoke
Required workflow control: serialized concurrency group shared with any registry publication path
```

## File Matrix

| Action | Path | Purpose |
|---|---|---|
| CREATE | `deploy/docker/web.Dockerfile` | root-context non-root web image; build can verify canonical dashboard assets |
| CREATE | `deploy/docker/analytics-api.Dockerfile` | root-context non-root analytics API image over `src/agriinsight/analytics_api` |
| CREATE | `deploy/docker/web.Dockerfile.dockerignore` | allow only web sources and canonical visual catalog/binaries from root context |
| CREATE | `deploy/docker/analytics-api.Dockerfile.dockerignore` | allow only Python package/runtime assets from root context |
| MODIFY | `.github/workflows/publish-images.yml` | add two matrix entries and serialize all four protected publications without weakening existing backend/Python gates |
| MODIFY | `.github/workflows/web-quality.yml` | build/scan web and analytics candidates without publication credentials |
| CREATE | `scripts/smoke-image-digest.ps1` | digest boot/smoke helper |
| CREATE | `scripts/generate-image-sbom.ps1` | SBOM/provenance helper |
| CREATE | `deploy/compose.release-overlay.yaml` | preferred compose overlay for release smoke with read-only mounts |
| CREATE | `deploy/compose.web-demo-overlay.yaml` | real OIDC + migrations + guarded demo seed/reconciliation + pipeline/backend/analytics/web demo path |
| MODIFY | `README.md` | gated release wording, badges, and package links |
| MODIFY | `docs/deployment-guide.md` | deployment + rollback guide |
| MODIFY | `docs/system-architecture.md` | container/runtime topology |
| CREATE | `plans/260722-2342-production-web-platform/reports/github-social-preview-owner-handoff.md` | manual owner handoff if API support is missing |

## TDD Plan

### RED

1. Add container/context smoke tests that fail if Dockerfile-specific ignore rules omit required web/catalog/Python files, include secrets/local artifacts, run as root, need a writable root/source mount, cannot read mounted artifacts read-only, or lack health checks.
2. Add workflow assertions that fail on `latest`, missing pre-push and exact-digest Trivy gates, missing SBOM/provenance, more than one matrix publication in flight, or any regression to existing Python/backend entries.
3. Add README/repo-metadata checks that fail if copy says "production" while the external gate flag is open.
4. Add workflow checks that fail if credentials are expected inline rather than through protected secrets/OIDC.

### GREEN

1. Build both Dockerfiles from root context with Dockerfile-specific allowlists, explicit UID/GID, read-only root defaults, and controlled temp dirs.
2. Extend the verified `release-images` workflow matrix and force serial publication while preserving pinned actions/protected environment/immutable tags/existing smoke tests; add a pre-push candidate scan before retaining exact remote-digest verification.
3. Add smoke, scan, and digest verification scripts.
4. Add the demo overlay with web role bootstrap/migrator/runtime separation, pinned upstream Keycloak/PostgreSQL, environment-only credentials, guarded Phase 2 seed/reconciliation, and health-based ordering.
5. Update README, deployment docs, system architecture, and GitHub repo About/social preview handoff to the gated-release wording.

### REFACTOR

1. Consolidate shared release steps into one reusable workflow.
2. Reuse scan/provenance helpers between web and analytics images.
3. Keep release wording in one config source so docs and repository About stay in sync.

## Implementation Steps

1. Build and smoke both images locally with context-allowlist, non-root/read-only-root, and pre-publication scan constraints.
2. Prove the demo overlay end-to-end: real OIDC login, separate migrations, guarded seed/reconciliation, pipeline, API readiness, and web role journey.
3. Add SBOM, provenance, and digest-smoke evidence generation.
4. Extend the existing publication workflow; add web/analytics conditions without changing established Python/backend coordinates or removing smoke/scan gates.
5. Add README package links/badges, GitHub repo About metadata, and social-preview handoff.
6. Validate the protected gate behavior last: push only when the external approval signal is actually closed.

## Commands

Focused:

```powershell
docker build -f deploy/docker/web.Dockerfile -t nguyenson1710/agriinsight-web:sha-local .
docker build -f deploy/docker/analytics-api.Dockerfile -t nguyenson1710/agriinsight-analytics-api:sha-local .
trivy image --severity HIGH,CRITICAL --exit-code 1 nguyenson1710/agriinsight-web:sha-local
trivy image --severity HIGH,CRITICAL --exit-code 1 nguyenson1710/agriinsight-analytics-api:sha-local
gh repo edit JasonTM17/AgriInsight --description "Enterprise agriculture analytics with Spring Boot, FastAPI, Next.js, and Bronze-Silver-Gold data architecture." --homepage "https://github.com/JasonTM17/AgriInsight#readme" --add-topic agriculture --add-topic analytics --add-topic data-engineering --add-topic spring-boot --add-topic fastapi --add-topic nextjs
```

Broad:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/check-workspace-disk.ps1
docker compose -f compose.yaml -f deploy/compose.release-overlay.yaml config --quiet
docker compose -f compose.yaml -f compose.backend.yaml -f deploy/compose.web-demo-overlay.yaml config --quiet
```

## Acceptance Criteria

- [ ] Web and analytics API images boot as non-root and do not require writable app code mounts.
- [ ] Dockerfile-specific context allowlists include required code/catalog assets and exclude `.env`, local artifacts, caches, VCS metadata, and unrelated services.
- [ ] Web and analytics build/scan jobs can run independently, but publication is serialized and protected-gated.
- [ ] Semantic and SHA tags are the only publishable tags; `latest` is impossible by workflow design.
- [ ] SBOM, provenance, Trivy zero HIGH/CRITICAL, and pull-by-digest smoke evidence exist for both images.
- [ ] The existing protected workflow retains Python/backend behavior, adds web/analytics, and serializes all four matrix publications with one tag/gate/evidence model.
- [ ] Pre-publication Trivy zero HIGH/CRITICAL blocks push; the exact published digest is scanned and smoked again.
- [ ] The opt-in demo overlay proves real OIDC login through all services with reconciled big-data masters and separate web/backend migration/runtime roles; PostgreSQL/Keycloak remain upstream-only.
- [ ] README, deployment docs, architecture docs, GitHub repository About metadata, and any social-preview owner handoff reflect the same gated-release posture.
- [ ] No web-app About page or ninth IA surface is added by this phase.
- [ ] If the external publication gate is open, outputs are labeled internal candidate only and no production claim is emitted.

## Risks And Rollback

| Risk | Likelihood x Impact | Mitigation | Rollback |
|---|---|---|---|
| Extending the release matrix regresses existing backend/Python publication | Medium x High | workflow contract tests, preserved coordinates/steps, `max-parallel: 1` | revert only new matrix entries and keep local web/analytics candidates |
| Image needs writable root filesystem in prod | Medium x High | read-only smoke and temp-dir tests first | revert to prior digest; do not publish new one |
| Repo metadata or docs claim production too early | Medium x High | gate-aware copy test + one shared status source | revert copy; retain candidate wording |
| Trivy or provenance gate fails late | Medium x Medium | run local scan/evidence before remote publication | stop publication; fix image and rerun |
| Root `.dockerignore` excludes future web/deploy files or sends secrets | High x High | Dockerfile-specific allowlists plus context-content tests | block build and fix allowlist before any scan/push |
| GitHub API cannot set social preview | Medium x Low | create owner handoff artifact instead of fake automation | keep existing preview and document manual next step |

## Dependencies And Ownership

- Depends entirely on Phase 11 sign-off.
- Owns only Dockerfiles, release workflows, release helpers, release-facing docs, and GitHub repository metadata/handoff artifacts.
- Must not modify business logic or backend publication files outside the dedicated release path for these two images.

## Commit Slices

1. `build(images): add non-root web and analytics images`
2. `ci(images): extend protected serialized publication matrix`
3. `docs(release): align candidate and repository metadata`

## Locked External Controls

- Publication binds to the existing `release-images` GitHub environment, repository variable `DOCKERHUB_NAMESPACE`, environment secrets `DOCKERHUB_USERNAME`/`DOCKERHUB_TOKEN`, and `GITHUB_TOKEN` package permission; no parallel manual flag is invented.
- `gh repo edit` owns description/homepage/topics. Social preview upload is an explicit repository-owner handoff because the CLI path is not treated as an automated release guarantee.
