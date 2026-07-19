# Crop health page override

Status: design contract only. Current evidence is batch Gold data; realtime and ML confidence are explicitly deferred.

## User and decision

- Primary roles: agronomist/farm manager; executive receives summary only.
- Primary job: turn sensor, weather, observation, and pest evidence into one bounded field-risk decision.
- Primary action: open the highest-risk field, inspect lineage/freshness, and assign the recommended verification action.
- Invariant: observed evidence, configured threshold, heuristic risk, and future model output are visibly different concepts.

## Data contract

| Surface | Current source | Required context |
|---|---|---|
| Risk summary | `crop_health_summary.csv`, `crop_health_alerts.csv` | score/status, threshold label, cutoff |
| Field evidence | `field_health_status.csv` | field/farm/crop, sensor age, moisture, pH, rain, pest evidence |
| Trends | `environment_daily.csv`, `pest_incidents_weekly.csv` | unit, aggregation, missing intervals |
| Future action workflow | Phase 4 operational activity API | assignee, status, optimistic version, audit context |

Do not describe data as live when the manifest is batch. Do not display a probability/confidence until a versioned model contract exists.

## Structure and interaction

1. Risk queue sorted by severity, evidence age, then operational scope; manual sort never silently changes the priority definition.
2. Field/plot context with crop and season label, accessible risk pattern, last evidence time, and recommended next check.
3. Time-series panel with threshold lines, missing-data gaps, aggregation control, text summary, and table fallback.
4. Evidence drawer showing source, sensor/observation identity, run/cutoff, value/unit, and quality flags.
5. Action handoff is disabled until operational auth/API contracts exist; design fixture labels that capability unavailable.

## State contract

| State | Required treatment |
|---|---|
| Loading | Plot and trend reserve dimensions; no animated map noise |
| No risk alerts | Show monitored-field/freshness coverage, not a celebratory false green |
| Stale sensor | Age and last reading lead the panel; recommended connectivity check |
| Missing series | Visible gap and unavailable interval; never interpolate silently |
| Partial field scope | State how many authorized fields are included without revealing others |
| Permission denied | Generic field denial; no guessed field/farm metadata |
| Evidence failed | Keep risk row, mark evidence unavailable, provide correlation-based retry |

## URL, responsive, and accessibility

- URL owns risk status, farm/field, crop, period, aggregation, and selected evidence. Back returns to the same queue position.
- Mobile prioritizes the highest-risk field and most recent evidence; dense series are aggregated with a detail route.
- Risk uses icon + label + pattern. Plot map has a text equivalent. Interactive chart points are keyboard reachable in production.

## Acceptance

- High/watch/healthy, stale/offline, missing interval, partial scope, denied, empty, and evidence-error fixtures are approved before implementation.
