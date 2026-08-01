from __future__ import annotations

from datetime import date

import pandas as pd
import pytest

from agriinsight.yield_forecast import (
    FORECAST_COLUMNS,
    MODEL_VERSION,
    YieldForecastError,
    forecast_active_season_yield,
)
from tests.yield_forecast_test_data import (
    candidate as _candidate,
    candidate_frame as _candidate_frame,
    history_frame as _history_frame,
    history_row as _history_row,
    ready_history as _ready_history,
)


def test_ready_forecast_matches_known_point_span_and_season_start_backtest() -> None:
    result = forecast_active_season_yield(
        _history_frame(_ready_history()),
        _candidate_frame([_candidate()]),
        date(2026, 7, 18),
    ).iloc[0]

    assert list(result.index) == list(FORECAST_COLUMNS)
    assert result["model_version"] == MODEL_VERSION
    assert result["forecast_status"] == "ready"
    assert result["forecast_origin_date"] == "2026-01-01"
    assert result["history_seasons"] == 10
    assert result["backtest_origins"] == 5
    assert result["backtest_seasons"] == 5
    assert result["forecast_yield_kg_per_ha"] == pytest.approx(130)
    assert result["observed_min_yield_kg_per_ha"] == pytest.approx(100)
    assert result["observed_max_yield_kg_per_ha"] == pytest.approx(160)
    assert result["forecast_quantity_kg"] == pytest.approx(260)
    assert result["observed_min_quantity_kg"] == pytest.approx(200)
    assert result["observed_max_quantity_kg"] == pytest.approx(320)
    assert result["backtest_mae_kg_per_ha"] == pytest.approx(20)
    assert result["backtest_wape_pct"] == pytest.approx(14.285714)


def test_multiple_harvest_events_sum_once_without_multiplying_area() -> None:
    rows = _ready_history()
    original = rows.pop(0)
    rows.extend(
        [
            {**original, "harvest_id": "HARVEST-SPLIT-01", "harvest_quantity_kg": 40},
            {**original, "harvest_id": "HARVEST-SPLIT-02", "harvest_quantity_kg": 60},
        ]
    )

    result = forecast_active_season_yield(
        _history_frame(rows),
        _candidate_frame([_candidate()]),
        date(2026, 7, 18),
    ).iloc[0]

    assert result["forecast_status"] == "ready"
    assert result["history_seasons"] == 10
    assert result["observed_min_yield_kg_per_ha"] == pytest.approx(100)


def test_target_context_and_future_nondate_fields_cannot_change_forecast() -> None:
    history = _history_frame(_ready_history())
    candidates = _candidate_frame([_candidate()])
    with_targets = candidates.assign(target_yield_kg=1.0)
    future = _history_row(2027, 1, 999)
    future.update(
        farm_code=" ",
        field_code=None,
        crop_code="bad",
        season_start_date="not-a-date",
        season_area_ha="bad",
        harvest_quantity_kg="bad",
    )

    baseline = forecast_active_season_yield(
        history,
        candidates,
        date(2026, 7, 18),
    )
    changed = forecast_active_season_yield(
        _history_frame([*_ready_history(), future]),
        with_targets.assign(target_yield_kg=999_999_999),
        date(2026, 7, 18),
    )

    assert baseline.to_dict("records") == changed.to_dict("records")


def test_insufficient_history_withholds_point_span_and_error_evidence() -> None:
    rows = [_history_row(2025, index, 100 + index) for index in range(1, 5)]

    result = forecast_active_season_yield(
        _history_frame(rows),
        _candidate_frame([_candidate()]),
        date(2026, 7, 18),
    ).iloc[0]

    assert result["forecast_status"] == "insufficient_history"
    assert result["history_seasons"] == 4
    for column in (
        "forecast_yield_kg_per_ha",
        "observed_min_yield_kg_per_ha",
        "observed_max_yield_kg_per_ha",
        "forecast_quantity_kg",
        "observed_min_quantity_kg",
        "observed_max_quantity_kg",
        "backtest_mae_kg_per_ha",
        "backtest_wape_pct",
    ):
        assert pd.isna(result[column])


def test_empty_history_keeps_explicit_insufficient_evidence() -> None:
    result = forecast_active_season_yield(
        _history_frame([]),
        _candidate_frame([_candidate()]),
        date(2026, 7, 18),
    ).iloc[0]

    assert result["forecast_status"] == "insufficient_history"
    assert result["history_seasons"] == 0
    assert pd.isna(result["history_start_at"])
    assert pd.isna(result["history_end_at"])


def test_output_is_stable_sorted_empty_and_finite() -> None:
    rows = _ready_history()
    candidates = [_candidate(), {**_candidate(), "season_code": "SEASON-2026-1000"}]
    shuffled = _history_frame(rows).sample(frac=1, random_state=17)

    first = forecast_active_season_yield(
        shuffled,
        _candidate_frame(list(reversed(candidates))),
        date(2026, 7, 18),
    )
    second = forecast_active_season_yield(
        shuffled,
        _candidate_frame(list(reversed(candidates))),
        date(2026, 7, 18),
    )
    empty = forecast_active_season_yield(
        _history_frame([]),
        _candidate_frame([]),
        date(2026, 7, 18),
    )

    assert first.to_dict("records") == second.to_dict("records")
    assert first["season_code"].tolist() == ["SEASON-2026-1000", "SEASON-2026-9001"]
    assert empty.empty
    assert list(empty.columns) == list(FORECAST_COLUMNS)


def test_aggregate_overflow_fails_closed() -> None:
    rows = _ready_history()
    rows.extend(
        [
            {**rows[0], "harvest_id": "OVERFLOW-1", "harvest_quantity_kg": 1e308},
            {**rows[0], "harvest_id": "OVERFLOW-2", "harvest_quantity_kg": 1e308},
        ]
    )

    with pytest.raises(YieldForecastError, match="finite numeric range"):
        forecast_active_season_yield(
            _history_frame(rows),
            _candidate_frame([_candidate()]),
            date(2026, 7, 18),
        )
