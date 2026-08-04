# GitHub release controls and social preview handoff

Date: 2026-07-27

Status: release controls retained; social-preview handoff superseded

## Already automated

- `main` is the default branch and the repository is public.
- `DOCKERHUB_NAMESPACE=nguyenson1710` exists as a repository variable.
- `.github/workflows/publish-images.yml` accepts exact `vX.Y.Z` tags only.
- Python, backend, web, and analytics API publication is serialized through the
  `release-images` environment.
- Candidate scan and non-root/read-only smoke run before registry login.
- Published images use semantic and full-SHA tags; `latest` is never produced.
- BuildKit provenance/SBOM, exact-digest Trivy scan, digest smoke, and
  Docker Hub/GHCR digest equality are mandatory.

## External controls still missing

GitHub API inspection on 2026-07-27 returned no repository environments, no
Actions secrets, and no branch protection or ruleset for `main`. Existing
private GHCR packages are `agriinsight-python` and `agriinsight-backend`.
Docker Hub inspection found the matching existing public Python/backend
repositories. Neither registry has `agriinsight-web` or
`agriinsight-analytics-api` yet. Do not create a release tag until all items
below are complete.

1. Protect `main` with the exact CI status checks and review policy selected by
   the owner; confirm the policy on a disposable pull request before enforcing
   it on normal work.
2. In **Settings → Environments**, create `release-images`.
3. Add required reviewers and deployment-branch/tag protection. Enable
   prevent-self-review where the repository plan supports it.
4. Add environment secrets `DOCKERHUB_USERNAME` and `DOCKERHUB_TOKEN`. Use a
   least-privilege Docker Hub access token; never a password or checked-in
   dotenv value.
5. Create the Docker Hub repositories `agriinsight-web` and
   `agriinsight-analytics-api`; reconfirm visibility for those and the existing
   `agriinsight-python`/`agriinsight-backend` repositories.
6. Confirm GHCR package visibility after the first protected publication.
7. Approve one exact semantic tag only after the CI run on the selected commit
   is green. Record all four returned digests and workflow URL.

## Social preview

The source image is
[`docs/assets/agriinsight-social-preview.jpg`](../../../docs/assets/agriinsight-social-preview.jpg).
The authenticated owner upload and unauthenticated metadata verification were
completed on 2026-08-04. The public `og:image` and `twitter:image` object is
1280x640 and matches the tracked source SHA-256 exactly. See the
[social-preview verification](../../260803-2156-portfolio-media-completion/reports/social-preview-verification.md).
No manual social-preview action remains.

## Release evidence to retain

- green source CI run and selected commit SHA;
- protected publication run URL and reviewer approval;
- four Docker Hub/GHCR digest pairs;
- SBOM and provenance attestations;
- zero HIGH/CRITICAL pre-publish and exact-digest Trivy results;
- non-root/read-only exact-digest smoke output;
- rollback tag/digest selected by the operator.

## Unresolved questions

- Required reviewer and release-token rotation owner.
- Exact `main` review count, required CI checks, and administrator bypass policy.
- Docker Hub/GHCR visibility policy.
- Repository license owner/choice; candidate OCI labels intentionally omit a
  license until a root license file exists, so no README license badge was
  added.
- Production OIDC, hostname/TLS, observability, backup, and recovery owners.
