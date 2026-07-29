---
title: Inventory Demand Forecasting
description: >-
  Add a deterministic, backtested 30-day inventory-demand forecast and carry it
  from warehouse facts through Gold, API, and dashboard evidence.
status: pending
priority: P1
branch: main
tags:
  - feature
  - analytics
  - forecasting
  - inventory
blockedBy: []
blocks: []
created: '2026-07-29T13:41:37.414Z'
createdBy: 'ck:plan'
source: skill
---

# Inventory Demand Forecasting

## Overview

Replace the current trailing-30-day value labelled as a prediction with an
honest, versioned forecasting data product. Start with a dependency-free
90-day mean baseline, rolling-origin backtest, empirical 30-day range, and
explicit insufficient-history state. Later phases adopt the forecast in Gold
decision support and expose the same server-computed evidence in the existing
Inventory experiences.

This plan advances the persistent predictive-analytics goal without claiming
yield, pest-risk, anomaly, or what-if coverage. Those remain separate follow-up
plans after this first forecasting contract is accepted end-to-end.

## Phases

| Phase | Name | Status |
|-------|------|--------|
| 1 | [Forecast contract and backtested baseline](./phase-01-forecast-contract-and-backtested-baseline.md) | Completed |
| 2 | [Gold pipeline and decision integration](./phase-02-gold-pipeline-and-decision-integration.md) | Completed |
| 3 | [API dashboard and hosted acceptance](./phase-03-api-dashboard-and-hosted-acceptance.md) | Pending |

## Dependencies

- Consumes the existing Bronze–Silver–Gold pipeline and inventory star-schema
  facts. No new database, broker, model provider, or secret.
- Consumes the accepted analytics API and Field Ledger Inventory boundaries.
  The blocked external production deployment plan does not block source/hosted
  acceptance here.
- Heavy browser, image, and integration gates run in hosted CI while the local
  C/D disk guard is below threshold.

## Acceptance boundary

- Phase completion requires reproducible output, focused tests, regression
  tests, documentation, and no future-data leakage.
- Forecasts are decision support. They never submit purchase orders or mutate
  inventory automatically.
- Model/version/status/backtest evidence travel with every forecast; UI does
  not recalculate predictions.
