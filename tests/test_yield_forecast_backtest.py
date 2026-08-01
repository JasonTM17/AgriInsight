from __future__ import annotations

from datetime import date

import pandas as pd
import pytest

from agriinsight.yield_forecast import forecast_active_season_yield
from tests.yield_forecast_test_data import (
    candidate as _candidate,
    candidate_frame as _candidate_frame,
    history_frame as _history_frame,
    history_row as _history_row,
)


def test_equal_origin_outcomes_never_train_each_other() -> None:
    rows = [
        *[
            _history_row(2024, index, 100, crop_code="COFFEE")
            for index in range(1, 6)
        ],
        _history_row(2025, 1, 0, crop_code="COFFEE", start_day=1),
        _history_row(2025, 2, 1_000, crop_code="COFFEE", start_day=1),
        _history_row(2025, 3, 100, crop_code="COFFEE", start_day=2),
    ]

    result = forecast_active_season_yield(
        _history_frame(rows),
        _candidate_frame([_candidate(crop_code="COFFEE")]),
        date(2026, 7, 18),
    ).iloc[0]

    assert result["forecast_status"] == "ready"
    assert result["backtest_origins"] == 2
    assert result["backtest_seasons"] == 3
    assert result["backtest_mae_kg_per_ha"] == pytest.approx(1_000 / 3)
    assert result["backtest_wape_pct"] == pytest.approx(100_000 / 1_100)


def test_zero_backtest_actual_denominator_prevents_ready_status() -> None:
    rows = [
        _history_row(year, index, 0)
        for year in (2024, 2025)
        for index in range(1, 6)
    ]

    result = forecast_active_season_yield(
        _history_frame(rows),
        _candidate_frame([_candidate()]),
        date(2026, 7, 18),
    ).iloc[0]

    assert result["history_seasons"] == 10
    assert result["backtest_origins"] == 5
    assert result["forecast_status"] == "insufficient_history"
    assert pd.isna(result["backtest_wape_pct"])
    assert pd.isna(result["forecast_quantity_kg"])
