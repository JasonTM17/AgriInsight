# Phase 4 — protected release and package publication

Status: completed 2026-08-01

## Release coordinates

- Version: [`v0.4.0`](https://github.com/JasonTM17/AgriInsight/releases/tag/v0.4.0)
- Annotated tag object: `4c27b343eecd32cf7daac462e5f661011e2af0df`
- Peeled tag and release `main` SHA:
  `616527dcc7f4a03720fb48e617f9310ab9614873`
- Exact-head CI:
  [`30697294137`](https://github.com/JasonTM17/AgriInsight/actions/runs/30697294137)
  completed 10/10 before the tag was created.
- Protected publication:
  [`30697808763`](https://github.com/JasonTM17/AgriInsight/actions/runs/30697808763)
  completed 4/4 through the normal required-reviewer environment path.

The Phase 3 feature head `54947ab7a34733273ca3c0e3b76d2cdfe647d94b`
and hosted media merge SHA `ecfe58ccceee923e43951ce6b3a942581e62a298`
remain the source acceptance and artifact provenance. PR #15 was rebase-merged;
the immutable release coordinate is therefore the later exact CI-verified
`main` SHA above, not an assertion that the pre-rebase feature SHA is its Git
ancestor.

## Registry evidence

Each image has four independently inspected references: Docker Hub semantic,
Docker Hub full-SHA, GHCR semantic, and GHCR full-SHA. All four references in
each row resolved to the same recorded digest.

| Image | Docker Hub | GHCR | Full-SHA tag | Immutable digest |
|---|---|---|---|---|
| Python | `nguyenson1710/agriinsight-python:0.4.0` | `ghcr.io/jasontm17/agriinsight-python:0.4.0` | `sha-616527dcc7f4a03720fb48e617f9310ab9614873` in both registries | `sha256:0c4889671ce010e8d806f949d508c69938d55effa2429e428e71ba5e7ef77420` |
| Backend | `nguyenson1710/agriinsight-backend:0.4.0` | `ghcr.io/jasontm17/agriinsight-backend:0.4.0` | `sha-616527dcc7f4a03720fb48e617f9310ab9614873` in both registries | `sha256:c8a21a01b83386d75d4f259103245dbf8f7ffa0730a2ac9ee4e39686c407f3d9` |
| Web | `nguyenson1710/agriinsight-web:0.4.0` | `ghcr.io/jasontm17/agriinsight-web:0.4.0` | `sha-616527dcc7f4a03720fb48e617f9310ab9614873` in both registries | `sha256:da49816d51c349391676b7800beffb5270fd27186be3e1d3b9e95aa128fbc345` |
| Analytics API | `nguyenson1710/agriinsight-analytics-api:0.4.0` | `ghcr.io/jasontm17/agriinsight-analytics-api:0.4.0` | `sha-616527dcc7f4a03720fb48e617f9310ab9614873` in both registries | `sha256:ce0ff7e0d40ad2851355b2274b729059677380d0351b51993582377316928c02` |

The workflow published only these four first-party images. It created no
`latest` tag, fifth service, or AgriInsight copy of upstream PostgreSQL.

## Supply-chain and runtime gates

All four jobs passed:

- local candidate Trivy policy for `vuln,secret,misconfig`, severities
  `HIGH,CRITICAL`, `ignore-unfixed=true`;
- local candidate non-root/read-only smoke;
- build and publication with SBOM/provenance requested;
- non-null GHCR provenance and SBOM presence checks;
- exact published-digest Trivy policy, pull, and non-root/read-only smoke; and
- semantic Docker Hub/GHCR digest equality in the hosted workflow.

The independent post-run inspection added the missing full-SHA parity proof
for all 16 references. This evidence does not claim signature verification,
attestation-content validation, or Docker Hub referrer parity. Runtime users
were `agriinsight` for Python and `10001:10001` for backend, web, and analytics
API; smoke used a read-only root filesystem and dropped runtime privileges.

## GitHub Release media

The public GitHub Release contains the same accepted Phase 3 media bytes:

| Asset | Bytes | SHA-256 |
|---|---:|---|
| `yield-forecast-desktop.webp` | 45,520 | `d16e37cc75d0c20b253f61dc9db6d47a923f831d856f2e80e429ea388beffb73` |
| `yield-forecast-mobile.webp` | 61,298 | `a508fc698ea4fa5c34d3b6ee46e2312bed8a3fb2377b42257821565648843124` |
| `agriinsight-yield-forecast-loop.gif` | 69,400 | `5262363262f15055bcd2ffd63955268c4ff129fa907ed0f3a5fb74eae199198c` |

GitHub reported the same three byte counts after upload, and the Release body
records all three SHA-256 values.

## Documentation and final gate

The release updates `README.md`, the deployment guide, project overview,
roadmap, codebase summary, Phase 3 linkage, and this Phase 4 evidence.
Post-release documentation commit
`337391bc76dd82123dc12af7f22e039884df28f2` passed all 10 jobs in hosted CI
[`30699659447`](https://github.com/JasonTM17/AgriInsight/actions/runs/30699659447).
That final acceptance gate completes Phase 4.

## Rollback and evidence boundary

Rollback selects the prior semantic four-image set with its compatible Gold
artifacts; the published `v0.4.0` tag is never moved or reused.

This release proves hosted source CI, protected registry publication, immutable
tag parity, supply-chain presence gates, hardened smoke, and documentation
media integrity. It does not approve external VPS/public ingress, production
OIDC, ingress rate limiting, successful-read audit retention, agronomic ground
truth, confidence intervals, model accuracy/SLA, or automatic operational
action.

## Unresolved questions

- Which owners approve external hosting, production OIDC, ingress rate limits,
  successful-read audit retention, backup/recovery objectives, and model SLA?
- Should the currently private GHCR packages become public? That visibility
  change is separate from this verified protected publication.
