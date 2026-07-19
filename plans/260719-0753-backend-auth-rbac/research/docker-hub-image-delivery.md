# Docker Hub image-delivery research

Verified: 2026-07-19
Scope: first-party AgriInsight images only.

## Primary sources

- [Docker personal access tokens](https://docs.docker.com/security/access-tokens/) documents PAT use for CI/CD, scoped permissions, expiration, revocation, and the requirement to treat tokens as secrets. Organization automation should prefer an organization access token when applicable.
- [Docker GitHub Actions attestations](https://docs.docker.com/build/ci/github-actions/attestations/) documents BuildKit provenance and SBOM attestations through `docker/build-push-action`.
- [Docker SBOM attestations](https://docs.docker.com/build/metadata/attestations/sbom/) documents attaching an SBOM to a pushed image.
- [Docker login](https://docs.docker.com/reference/cli/docker/login/) documents PAT input and `--password-stdin`; workflow secrets must never appear in command arguments or logs.

## Decision

- Repositories: `${DOCKERHUB_NAMESPACE}/agriinsight-python`, `${DOCKERHUB_NAMESPACE}/agriinsight-backend`, then `${DOCKERHUB_NAMESPACE}/agriinsight-web` in the frontend milestone.
- CI authentication: protected environment secrets `DOCKERHUB_USERNAME` and `DOCKERHUB_TOKEN`; token has write, not delete/admin, where the account model permits. Prefer an organization token for an organization namespace.
- Trigger: pull requests and normal branch builds never authenticate or push. A protected approved version release builds once, runs gates, and pushes immutable semantic-version plus Git-SHA tags.
- Evidence: OCI source/revision/version labels, SBOM, provenance, vulnerability report, returned registry digest, and pull/run smoke result. The deployment identity is a digest, not `latest`.
- Scope: do not mirror PostgreSQL or other upstream images. Pin/verify upstream dependencies independently.

## Rejected shortcuts

- Docker Hub password in CI: too broad and harder to revoke safely than a scoped token.
- Automatic `latest`: mutable, hides rollback identity, and can move after an unrelated release.
- Push on every pull request: exposes credentials to a broad execution surface and creates unreviewed registry state.
- Local workstation as the production publisher: depends on mutable local state and consumes constrained C/Docker Desktop storage.
- Build secrets passed as Docker `ARG`: can leak into metadata/layers; use CI secret mounts only if a build genuinely needs a secret.

## Local constraint

Local build/run remains useful for smoke testing, but only after `scripts/check-workspace-disk.ps1` passes, Docker daemon is running, and Docker Desktop data is confirmed on D. Publication remains CI-controlled even when local login exists.

## Unresolved questions

- Personal or organization Docker Hub namespace?
- Public or private repositories?
- Approved vulnerability severity/exception SLA and image-retention policy?
