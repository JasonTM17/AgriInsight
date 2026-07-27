# Phase 12 Release Candidate Evidence

Date: 2026-07-27

Status: internal candidate accepted; external publication blocked

Candidate baseline: `ac09db8`

GitHub Actions: `30267362838`

## Candidate scope

- Python pipeline/dashboard image.
- Java backend image.
- Next web image.
- FastAPI analytics API image.
- Digest-pinned release and real-OIDC demo Compose overlays.
- One protected serialized Docker Hub/GHCR publication workflow.

## No-push image gate

| Image | Build | Trivy HIGH/CRITICAL | Non-root/read-only smoke |
|---|---|---|---|
| `agriinsight-python` | PASS | 0 | PASS |
| `agriinsight-backend` | PASS | 0 | PASS |
| `agriinsight-web` | PASS | 0 | PASS |
| `agriinsight-analytics-api` | PASS | 0 | PASS |

The web runtime is pinned to Node `24.18.0-bookworm-slim`, runs as
`10001:10001`, and removes npm/corepack/yarn from the runtime layer. Web and
analytics build contexts use explicit allowlists and exclude dotenv, VCS,
caches, build output, and unrelated services.

Post-baseline commit `8d50962` removes the unselected MIT claim from all four
OCI labels and adds the root-license invariant; the focused container contract
suite passes 11/11. The final head CI reruns the same image gates after this
metadata-only correction.

## Release contract

- `release-images` protected environment.
- Exact `vX.Y.Z` trigger only.
- `max-parallel: 1` across all four images.
- Semantic and full-SHA tags only; no `latest`.
- Candidate scan/smoke before registry authentication.
- BuildKit SBOM and provenance.
- Exact remote-digest Trivy scan, non-root/read-only smoke, and Docker
  Hub/GHCR digest equality before success.

Contract tests pass. The last five items that require an actual protected
publication are intentionally not claimed as executed evidence.

## Compose and repository evidence

- Release and demo overlay chains pass `docker compose ... config --quiet`.
- PostgreSQL and Keycloak remain pinned upstream dependencies and are never
  republished as AgriInsight images.
- GitHub About description, homepage, and 20 topics are populated.
- README embeds the 1280x640 social preview source and generated Field Ledger
  GIF.
- Manual social-preview, release-control, registry, and license actions are in
  `github-social-preview-owner-handoff.md`.

## External blockers

- No GitHub `release-images` environment, required reviewers, or Actions
  secrets.
- No Docker Hub `agriinsight-web` or `agriinsight-analytics-api` repository.
- No `main` branch protection/ruleset.
- No root license file; candidate OCI labels intentionally omit a license
  rather than making an unselected legal claim.
- Production OIDC, hostname/TLS, observability, backup, and recovery ownership
  remain undecided.

No tag, Docker Hub push, GHCR push, or production claim was made.

## Unresolved questions

- Release reviewer and token-rotation owner.
- Registry visibility policy and repository license.
- Production identity and operations owners.
