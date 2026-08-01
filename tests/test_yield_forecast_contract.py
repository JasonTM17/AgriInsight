from __future__ import annotations

from datetime import date

import pandas as pd
import pytest

from agriinsight.yield_forecast import (
    YieldForecastError,
    forecast_active_season_yield,
)
from tests.yield_forecast_test_data import (
    CANDIDATE_COLUMNS,
    HISTORY_COLUMNS,
    candidate as _candidate,
    candidate_frame as _candidate_frame,
    history_frame as _history_frame,
    history_row as _history_row,
    ready_history as _ready_history,
)


@pytest.mark.parametrize(
    ("frame_name", "field", "value", "message"),
    (
        ("history", "harvested_at", "today", "timezone-free timestamp"),
        ("history", "harvested_at", "9999-01-01T00:00:00", "timezone-free timestamp"),
        ("history", "season_completed_at", "2025-06-01T12:00:00Z", "timezone-free timestamp"),
        ("history", "season_start_date", "now", "timezone-free date"),
        ("history", "season_start_date", "2025-02-30", "timezone-free date"),
        ("history", "season_start_date", "9999-01-01", "timezone-free date"),
        ("history", "harvest_quantity_kg", -1, "non-negative finite"),
        ("history", "harvest_quantity_kg", True, "non-negative finite"),
        ("history", "season_area_ha", 0, "positive finite"),
        ("history", "crop_code", "rice", "canonical identifier"),
        ("history", "season_status", "active", "completed"),
        ("candidate", "season_start_date", "2026-01-01T12:00:00", "timezone-free date"),
        ("candidate", "expected_harvest_date", "bad", "timezone-free date"),
        ("candidate", "season_area_ha", float("nan"), "positive finite"),
        ("candidate", "season_status", "completed", "active"),
        ("candidate", "farm_code", " ", "canonical identifier"),
    ),
)
def test_invalid_contract_fails_closed(
    frame_name: str,
    field: str,
    value: object,
    message: str,
) -> None:
    history = _history_frame(_ready_history())
    candidate = _candidate_frame([_candidate()])
    target = history if frame_name == "history" else candidate
    target[field] = target[field].astype("object")
    target.loc[target.index[0], field] = value

    with pytest.raises(YieldForecastError, match=message):
        forecast_active_season_yield(
            history,
            candidate,
            date(2026, 7, 18),
        )


@pytest.mark.parametrize("frame_name", ("history", "candidate"))
def test_missing_and_duplicate_columns_fail_with_domain_error(frame_name: str) -> None:
    history = _history_frame(_ready_history())
    candidate = _candidate_frame([_candidate()])
    frame = history if frame_name == "history" else candidate
    required = HISTORY_COLUMNS if frame_name == "history" else CANDIDATE_COLUMNS
    missing = frame.drop(columns=[required[0]])
    duplicate = pd.concat([frame, frame[[required[0]]]], axis=1)

    with pytest.raises(YieldForecastError, match="missing required columns"):
        forecast_active_season_yield(
            missing if frame_name == "history" else history,
            missing if frame_name == "candidate" else candidate,
            date(2026, 7, 18),
        )
    with pytest.raises(YieldForecastError, match="column names must be unique"):
        forecast_active_season_yield(
            duplicate if frame_name == "history" else history,
            duplicate if frame_name == "candidate" else candidate,
            date(2026, 7, 18),
        )


def test_event_after_completion_and_impossible_candidate_chronology_fail() -> None:
    history = _history_frame(_ready_history())
    history.loc[0, "harvested_at"] = "2025-07-01T12:00:00"
    history.loc[0, "season_completed_at"] = "2025-06-01T12:00:00"

    with pytest.raises(YieldForecastError, match="completion"):
        forecast_active_season_yield(
            history,
            _candidate_frame([_candidate()]),
            date(2026, 7, 18),
        )

    history = _history_frame(_ready_history())
    history.loc[0, "harvested_at"] = "2024-12-31T08:00:00"
    with pytest.raises(YieldForecastError, match="completion"):
        forecast_active_season_yield(
            history,
            _candidate_frame([_candidate()]),
            date(2026, 7, 18),
        )

    candidate = _candidate(expected_harvest_date=date(2025, 12, 31))
    with pytest.raises(YieldForecastError, match="candidate chronology"):
        forecast_active_season_yield(
            _history_frame(_ready_history()),
            _candidate_frame([candidate]),
            date(2026, 7, 18),
        )


def test_duplicate_event_and_conflicting_season_context_fail() -> None:
    rows = _ready_history()
    rows.append({**rows[0]})
    with pytest.raises(YieldForecastError, match="harvest_id"):
        forecast_active_season_yield(
            _history_frame(rows),
            _candidate_frame([_candidate()]),
            date(2026, 7, 18),
        )

    rows = _ready_history()
    rows.append(
        {
            **rows[0],
            "harvest_id": "SECOND-EVENT",
            "season_area_ha": 2,
        }
    )
    with pytest.raises(YieldForecastError, match="season context"):
        forecast_active_season_yield(
            _history_frame(rows),
            _candidate_frame([_candidate()]),
            date(2026, 7, 18),
        )


def test_candidate_and_history_season_codes_cannot_overlap() -> None:
    candidate = _candidate()
    candidate["season_code"] = "SEASON-2025-0001"

    with pytest.raises(YieldForecastError, match="active and completed"):
        forecast_active_season_yield(
            _history_frame(_ready_history()),
            _candidate_frame([candidate]),
            date(2026, 7, 18),
        )


def test_as_of_date_must_be_exact_date() -> None:
    with pytest.raises(YieldForecastError, match="as_of_date must be a date"):
        forecast_active_season_yield(  # type: ignore[arg-type]
            _history_frame([_history_row(2025, 1, 100)]),
            _candidate_frame([_candidate()]),
            "2026-07-18",
        )


@pytest.mark.parametrize("frame_name", ("history", "candidate"))
def test_nullable_status_fails_closed(frame_name: str) -> None:
    history = _history_frame(_ready_history())
    candidate = _candidate_frame([_candidate()])
    frame = history if frame_name == "history" else candidate
    frame["season_status"] = frame["season_status"].astype("string")
    frame.loc[frame.index[0], "season_status"] = pd.NA

    with pytest.raises(YieldForecastError, match="season_status"):
        forecast_active_season_yield(
            history,
            candidate,
            date(2026, 7, 18),
        )


def test_pandas_timestamp_dates_are_supported() -> None:
    history = _history_frame(_ready_history())
    history["season_start_date"] = pd.to_datetime(history["season_start_date"])
    history["season_completed_at"] = pd.to_datetime(history["season_completed_at"])
    candidate = _candidate_frame([_candidate()])
    candidate["season_start_date"] = pd.Timestamp("2026-01-01")
    candidate["expected_harvest_date"] = pd.Timestamp("2026-10-01")

    result = forecast_active_season_yield(
        history,
        candidate,
        date(2026, 7, 18),
    )

    assert result.iloc[0]["forecast_status"] == "ready"
