---
title: Yield Forecasting
description: >-
  Add a deterministic, leakage-safe yield baseline for active seasons and carry
  its backtest evidence from warehouse facts through Gold, a scoped API, and the
  existing farm-detail experience.
status: in-progress
priority: P1
branch: feature/yield-forecasting
tags:
  - feature
  - analytics
  - forecasting
  - yield
blockedBy: []
blocks: []
created: '2026-07-30T03:24:31.718Z'
createdBy: 'ck:plan'
source: skill
---

# Yield Forecasting

## Overview

Create the first honest yield-forecast data product without adding a model
provider or a heavyweight ML dependency. The baseline predicts gross harvested
kilograms and kilograms per hectare for each active season from the median
realized yield of earlier completed seasons for the same crop. It publishes the
observed historical min/max span, season-start rolling-origin error evidence,
and explicit coverage states.

The current season target remains comparison context only; it is not a model
feature. Existing realized `yieldKgPerHa`, Farm Performance, cost, inventory,
and Overview contracts keep their meaning.

## Phases

| Phase | Name | Status |
|-------|------|--------|
| 1 | [Leakage-safe forecast contract and backtested baseline](./phase-01-leakage-safe-forecast-contract-and-backtested-baseline.md) | Completed |
| 2 | [Gold pipeline and snapshot integration](./phase-02-gold-pipeline-and-snapshot-integration.md) | Pending |
| 3 | [Scoped API farm dashboard and hosted acceptance](./phase-03-scoped-api-farm-dashboard-and-hosted-acceptance.md) | Pending |
| 4 | [Protected release and package publication](./phase-04-protected-release-and-package-publication.md) | Pending |

## Dependencies

- Uses the accepted Python Bronze–Silver–Gold warehouse, checksummed manifest,
  FastAPI snapshot gate, Spring-resolved FARMS authorization, tokenless BFF,
  and farm-detail route.
- Uses analytical `fact_harvest`, `dim_season`, `dim_field`, and `dim_crop` as
  the source. Live PostgreSQL-to-analytics ingestion is not introduced here.
- The external VPS/OIDC, production recovery ownership, and hosted DeepSeek SLO
  gates do not block source and hosted-CI acceptance.
- Heavy big-data, Docker, browser, image, and media work runs in hosted CI while
  local C/D disk guards remain active.

## Acceptance boundary

- Forecast grain is one active `season_code`; forecast target is gross
  `harvest_quantity_kg`, denominator is immutable `season_area_ha`, and all
  quantities remain explicit kg or kg/ha.
- Bronze/Silver/Warehouse must preserve an immutable `season_area_ha` snapshot
  and an explicit `completed_at` label-availability timestamp.
- Every forecast origin is the candidate season start. A training label may
  enter only when its completed timestamp is strictly earlier than that origin;
  facts after `as_of_date` remain ineligible everywhere.
- Model/version/status/history/span/backtest evidence travels with every
  published forecast. The browser formats evidence but performs no forecast
  math.
- `ready` requires at least five earlier same-crop seasons and two leakage-safe
  backtest origins. Sparse crops remain explicit `insufficient_history`; no
  accuracy is fabricated.
- Historical min/max values are descriptive observed spans, never confidence
  or prediction intervals and never a production agronomic SLA.
- This milestone does not implement pest-risk forecasting, anomaly detection,
  what-if analysis, Text-to-SQL, advanced ML, automatic operational mutations,
  or external production deployment.
