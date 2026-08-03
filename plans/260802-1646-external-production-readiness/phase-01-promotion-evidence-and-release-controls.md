---
phase: 1
title: Promotion evidence and release controls
status: completed
priority: P1
effort: 1-2d
dependencies: []
---

# Phase 1: Promotion evidence and release controls

## Overview

Make the supported promotion path reject mutable images and releases whose exact
commit has not passed the repository `ci.yml` workflow. Produce a non-secret
promotion-evidence contract that binds a deployment to the verified four-image
release.

## Requirements

- Functional: validate exact `@sha256:<64 hex>` references for Python, backend,
  web, and analytics API images before the release Compose configuration is
  used.
- Functional: require the deployment evidence to name tag, commit, CI and
  publication evidence, all four digests, executable rollback evidence,
  recovery proof, and owner-approved external gate references.
- Functional: require tag publication to query the repository `ci.yml` workflow
  and find a successful exact-`main` commit before registry authentication.
- Non-functional: never print secrets or accept placeholder evidence as a pass;
  retain paired Docker Hub/GHCR semantic and full-SHA digest parity.

## Architecture

`promotion-evidence.json` is a non-secret, operator-supplied record. The
PowerShell contract validates its structure, first-party image names, all four
selected environment values, rollback/recovery references, and current
approval validity. The supported release wrapper verifies exact GitHub workflow
metadata before it contacts Docker, then validates OCI source/revision/version
labels, attestations, paired tags, and the Compose configuration. Explicit
deploy and rollback modes wait for health or verify disable-exposure. Compose
remains a renderer; a direct `docker compose ... up` is not valid promotion
evidence.

## Related Code Files

- Create: `scripts/production-promotion-evidence-schema-helpers.psm1`
- Create: `scripts/validate-production-promotion-evidence.ps1`
- Create: `scripts/production-promotion-evidence-contract.psm1`
- Create: `scripts/start-production-release-compose.ps1`
- Create: `deploy/production-promotion-evidence.template.json`
- Create: `tests/test_production_promotion_evidence.py`
- Modify: `.github/workflows/publish-images.yml`
- Modify: `tests/test_container_release_contract.py`
- Modify: `docs/deployment-guide.md`
- Modify: `docs/production-readiness.md`

## Tests Before

1. Add execution tests that accept valid current/previous first-party digest
   references and reject tags, wrong components, untrusted repositories,
   expired/duplicate approval references, invalid rollback records, and
   mismatched environment values.
2. Add workflow contract assertions for exact-`ci.yml` verification before any
   registry login and parity for both semantic/full-SHA tags.
3. Add a wrapper contract assertion proving evidence validation precedes image
   pulls, OCI-label inspection, Actions checks, and Compose use.

## Implementation Steps

1. Define the smallest evidence schema needed for release identity, selected
   image references, rollback authority, recovery proof, and external approval
   links.
2. Implement a fail-closed contract with safe output only; bind all image names
   to approved first-party repositories, compare selected environment references,
   and reject expired, duplicate, or mistargeted approvals.
3. Add the supported wrapper that verifies exact Actions metadata before Docker,
   validates pulled OCI labels/attestations/paired tags, and runs explicit
   deploy or rollback modes with post-state verification.
4. Add an `actions: read` workflow permission and a no-secret GitHub API check
   against `ci.yml` for a successful exact-`main` commit, then prove semantic
   and full-SHA tag parity in both registries.
5. Document the supported preflight command immediately before the deployment
   command and prohibit direct Compose output from serving as promotion proof.

## Tests After

- Run focused Python contract tests and the validator against valid and invalid
  manifests without contacting Docker.
- Run YAML/JSON syntax checks, documentation validation, and diff whitespace
  checks without a registry login or deployment.

## Success Criteria

- [x] Mutable, untrusted, or wrong-component image references fail before
  Compose startup.
- [x] Placeholder, expired, duplicate, or incomplete owner evidence fails
  without leaking values.
- [x] The supported wrapper validates exact workflow metadata before Docker,
  validates current/prior image labels, and verifies deploy/rollback post-state
  before rendering or changing Compose.
- [x] Registry publication cannot authenticate until the exact `ci.yml`
  `main` CI is successful, and both semantic/full-SHA tags retain paired
  Docker Hub/GHCR digest parity.

## Risk Assessment

- GitHub Actions history, registry access, or local Docker can be unavailable.
  The wrapper fails closed with a retryable generic error; it never infers a
  green CI result or skips image metadata validation.
- Initial production deployment may have no prior digest. Require an explicit
  approved disable-exposure strategy rather than a fake previous tag.

## Security Considerations

- Evidence contains links and hashes only, never credentials or raw audit data.
- GitHub workflow metadata uses read-only access. Wrapper credentials come from
  the authorized target environment and are neither logged nor persisted.
