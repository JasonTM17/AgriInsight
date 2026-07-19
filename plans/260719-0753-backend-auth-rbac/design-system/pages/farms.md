# Farms page override

Status: design contract only. Read-only performance exists in Gold; operational farm/field/season APIs depend on backend phase 4.

## User and decision

- Primary roles: executive and farm manager; field worker receives only assigned work context.
- Primary job: compare farms, fields, and seasons, then drill to activity and harvest lineage.
- Primary action: isolate a target/yield/cost variance and open the exact season/field evidence.
- Scope invariant: guessed cross-tenant/cross-assignment IDs receive generic denial without names or counts.

## Data contract

| Surface | Current/future source | Required context |
|---|---|---|
| Farm comparison | `farm_performance.csv` | hectares, harvested area, yield, cost/ha, profit/margin, cutoff |
| Field health context | `field_health_status.csv` | crop, season, evidence age, risk/action |
| Farm/field/season master | Phase 4 operational API | UUID, canonical code, active/status, optimistic version |
| Activity/harvest lineage | Phase 4 operational API + approved analytics link | source event/run, correction lineage |

## Structure and interaction

1. URL-addressable farm/season/crop/status filters with active-scope summary.
2. Map/plot context paired with a sortable comparison table; selecting either synchronizes the other.
3. Comparison rows include hectares, yield, cost/ha, target variance, season status, and evidence/run link.
4. Detail route follows farm → field → season → activity/harvest lineage with a persistent breadcrumb/back path.
5. Create/edit lifecycle controls remain absent until Phase 4 authorization, validation, and conflict contracts are available.

## State contract

| State | Required treatment |
|---|---|
| Loading | Stable map/table split; no default marker flash |
| Empty tenant | Explain how an authorized admin provisions the first farm |
| Empty filter | Keep scope/filter state and offer reset; no fake row |
| Partial analytics | Show which metric/evidence is unavailable; preserve master data |
| Stale cutoff | Persistent banner attached to analytics measures |
| Permission denied | Generic denial; no foreign farm/field metadata |
| Version conflict | Future edit preserves draft and compares current version |

## URL, responsive, and accessibility

- URL owns farm, field, season, crop, status, sort, and page. Deep links retain filter state.
- Mobile map collapses behind a labeled “Xem sơ đồ” sheet; table becomes priority rows without hidden actions.
- Map selection has list/keyboard equivalent. Units are written beside every measure. Focus returns from sheet/detail to the invoking row.

## Acceptance

- Comparison, empty tenant/filter, stale/partial, denied, and future conflict fixtures exist; drill/back restores exact selection.
