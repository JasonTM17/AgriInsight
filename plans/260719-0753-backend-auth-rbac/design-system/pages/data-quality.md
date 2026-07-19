# Data quality page override

Status: design contract only. It reads manifest/quality artifacts and never repairs source data from the browser.

## User and decision

- Primary roles: data analyst/data steward; executive receives a trust summary.
- Primary job: decide whether the current run is fit for a named decision/export, then investigate the blocking evidence.
- Primary action: open a failing contract/column, inspect quarantined evidence, and follow the documented recovery owner/path.
- Invariant: a green aggregate never hides failed checks, quarantine rows, reconciliation drift, or stale data.

## Data contract

| Surface | Authoritative evidence |
|---|---|
| Run identity | manifest contract/version, run ID, seed, cutoff, generated time |
| Freshness/completeness/validity/uniqueness | current data-quality result artifacts |
| Quarantine | exact dataset/column/reason/count and approved sample policy |
| Reconciliation | Gold reconciliation artifacts and source/target counts |
| Export integrity | manifest rows/bytes/SHA-256 and normalized export request |

Run IDs, hashes, column names, and versions are `translate="no"` in production and available as full copyable values.

## Structure and interaction

1. Run header with status, cutoff, contract version, manifest verification, and decision-fit summary.
2. Dimension ledger for freshness, completeness, validity, uniqueness, reconciliation, and quarantine.
3. Issue table with severity, dataset/column, rule, count/share, owner, recovery hint, and evidence link.
4. Before/after transformation summary; no row-level personal/sensitive values in generic views.
5. Export manifest drawer with checksum, row/byte limits, and verification steps.

## State contract

| State | Required treatment |
|---|---|
| Loading | Preserve run header and issue-table geometry |
| No completed run | Explain the canonical pipeline command and required artifact boundary |
| Partial run | Identify completed/failed layers; never merge counts across cutoffs |
| Contract drift | Block fit/export claim; show expected vs observed version/columns |
| Quarantine present | Show count/share/owner; green checks remain separately visible |
| Manifest mismatch | Critical state with no download action |
| Artifact unavailable | Generic recovery and correlation/run ID; no local file-path leak |

## URL, responsive, and accessibility

- URL owns run ID, dimension, severity, dataset, owner, sort, and page; deep links restore exact evidence.
- Mobile shows severity/owner summary first, then expandable issue rows. Hash/run values never truncate without an accessible full/copy value.
- Tables retain semantic headers and keyboard sort. Charts always have exact counts and table alternatives.

## Acceptance

- Fresh green, stale, partial, drift, quarantine, reconciliation mismatch, manifest mismatch, absent run, and artifact-error fixtures are approved.
