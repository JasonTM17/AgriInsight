# Acceptance — Visual and data-scale checkpoint

Accepted: 2026-07-22
Scope: local Data Analytics MVP only
Status: accepted with one account-level social-preview handoff

## Accepted behavior

- `standard` remains the default, fast local/CI configuration.
- `big-data` resolves deterministically to 10 farms, 12 fields/farm, 60
  activities/season, 18 materials, 365 sensor days, and 24 readings/day.
- Explicit sizing flags override only their matching profile field.
- The manifest stores the selected profile, resolved dimensions, nominal sensor
  plan, and a deterministic run ID fingerprinted from seed/date/configuration.
- The guarded runner keeps Python temp/cache and artifacts on D and checks C/D
  before and after the workload.
- Six project-generated WebP visuals cover all dashboard areas; Crop Health
  visibly identifies generated demo evidence and never binds it to an
  observation ID.
- Missing visual files fail soft and do not block KPI, chart, filter, or export
  behavior.

## Big-data evidence

| Evidence | Result |
|---|---:|
| Run ID | `synthetic-2026-07-18-20260718-big-data-628b12344e35` |
| Nominal sensor plan | 1,051,200 |
| Bronze sensor rows | 1,050,003 |
| Silver sensor rows | 1,050,000 |
| Warehouse `fact_sensor_reading` | 1,050,000 |
| Crop activities | 14,400 |
| Inventory transactions | 4,608 |
| Quality status | passed |
| Checksum entries / mismatches | 74 / 0 |
| Artifact footprint | 388.2 MB on D |

The difference between nominal and generated sensor rows is expected: every
13th simulated sensor becomes stale five days early, then three intentional
duplicate/invalid/missing quality fixtures are added to Bronze. Cleaning yields
the exact 1,050,000 valid facts loaded into SQLite.

## Visual evidence

- Six assets decoded successfully; each WebP is below 350 KiB.
- Catalog hashes match the committed bytes.
- Social source is 1280 × 640 and 168 KiB.
- Streamlit theme matches the Field Ledger green/sage/harvest palette.
- Live browser smoke loaded the big-data Executive page and Crop Health page.
- Crop Health displayed the required “AI-generated demo evidence” warning.

## Verification

| Gate | Result |
|---|---|
| Profile/pipeline focused tests | 9 passed |
| Visual/dashboard focused tests | 13 passed |
| Full Python regression | 75 passed, 3 intentional optional-PDF skips |
| Python compileall | passed |
| Docs validator | 7 internal links valid; known Java/config scanner false positives only |
| Disk guard after full Python/UI gate | C 10.274 GB, D 25.364 GB; PASS/PASS |

The backend was not re-run because this checkpoint changed Python CLI/manifest,
Streamlit UI, assets, and documentation only. The last guarded backend evidence
remains 487 Surefire + 92 Failsafe, zero failures/errors/skips.

## Security and provenance review

- No credential or dotenv file was added.
- Generated visual source and intended usage are documented; no third-party
  stock attribution is implied.
- Crop imagery is not training data, ground truth, or medical/agronomic advice.
- Local big-data artifacts and browser screenshots remain ignored under
  `artifacts/`/`tmp/` and are not pushed.

## GitHub handoff

Repository description, topics, default `main`, merge policy, security
scanning, Dependabot, community files, issue forms, CODEOWNERS, and labels are
configured. The 1280 × 640 preview source is committed, but the authenticated
Chrome extension denied programmatic file selection. The remaining owner action
is to upload `docs/assets/agriinsight-social-preview.jpg` in repository Settings.

## Release boundary

This checkpoint does not publish Docker images or claim production web/evidence
capture. Phase 6 still owns PostgreSQL operating-cost/reporting APIs. Phase 7
still owns outbox, protected CI, scans, SBOM/provenance, registry publication,
backup/restore, and exact-digest smoke tests.
