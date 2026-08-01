from __future__ import annotations

from datetime import date

import pandas as pd

from agriinsight.yield_forecast_backtest import backtest_yield_labels
from agriinsight.yield_forecast_contract import (
    YieldForecastError as YieldForecastError,
    validated_forecast_inputs,
)
from agriinsight.yield_forecast_numeric import (
    finite_median,
    finite_product,
    finite_ratio,
    finite_sum,
)


MODEL_VERSION = "crop-median-yield-per-ha-v1"
MIN_TRAINING_SEASONS = 3
MIN_HISTORY_SEASONS = 5
MIN_BACKTEST_ORIGINS = 2

FORECAST_COLUMNS = (
    "as_of_date",
    "farm_code",
    "field_code",
    "season_code",
    "crop_code",
    "model_version",
    "forecast_status",
    "forecast_origin_date",
    "expected_harvest_date",
    "season_area_ha",
    "history_start_at",
    "history_end_at",
    "history_seasons",
    "backtest_origins",
    "backtest_seasons",
    "forecast_yield_kg_per_ha",
    "observed_min_yield_kg_per_ha",
    "observed_max_yield_kg_per_ha",
    "forecast_quantity_kg",
    "observed_min_quantity_kg",
    "observed_max_quantity_kg",
    "backtest_mae_kg_per_ha",
    "backtest_wape_pct",
)


def forecast_active_season_yield(
    history: pd.DataFrame,
    candidates: pd.DataFrame,
    as_of_date: date,
) -> pd.DataFrame:
    """Forecast gross active-season yield from earlier complete crop labels."""

    facts, active = validated_forecast_inputs(history, candidates, as_of_date)
    if active.empty:
        return pd.DataFrame(columns=FORECAST_COLUMNS)
    labels = _season_labels(facts)
    records = [
        _forecast_candidate(candidate, labels, as_of_date)
        for _, candidate in active.iterrows()
    ]
    return (
        pd.DataFrame.from_records(records, columns=FORECAST_COLUMNS)
        .sort_values(
            ["farm_code", "field_code", "season_code"],
            kind="stable",
        )
        .reset_index(drop=True)
    )


def _season_labels(facts: pd.DataFrame) -> pd.DataFrame:
    columns = (
        "season_code",
        "crop_code",
        "season_start_at",
        "season_completed_at",
        "yield_kg_per_ha",
    )
    records: list[dict[str, object]] = []
    for season_code, group in facts.groupby("season_code", sort=True):
        first = group.iloc[0]
        total = finite_sum(
            group["harvest_quantity_kg"],
            "completed season harvest quantity",
        )
        actual_yield = finite_ratio(
            total,
            float(first["season_area_ha"]),
            "completed season yield",
        )
        records.append(
            {
                "season_code": season_code,
                "crop_code": first["crop_code"],
                "season_start_at": first["season_start_date"],
                "season_completed_at": first["season_completed_at"],
                "yield_kg_per_ha": actual_yield,
            }
        )
    return pd.DataFrame.from_records(records, columns=columns)


def _forecast_candidate(
    candidate: pd.Series,
    labels: pd.DataFrame,
    as_of_date: date,
) -> dict[str, object]:
    origin = candidate["season_start_date"]
    crop_labels = labels[
        (labels["crop_code"] == candidate["crop_code"])
        & (labels["season_completed_at"] < origin)
    ].copy()
    history_count = len(crop_labels)
    backtest = backtest_yield_labels(
        labels,
        str(candidate["crop_code"]),
        crop_labels,
        minimum_training_seasons=MIN_TRAINING_SEASONS,
    )
    ready = (
        history_count >= MIN_HISTORY_SEASONS
        and backtest.origins >= MIN_BACKTEST_ORIGINS
        and backtest.seasons >= MIN_BACKTEST_ORIGINS
        and backtest.mae is not None
        and backtest.wape_pct is not None
    )

    point = lower = upper = None
    point_quantity = lower_quantity = upper_quantity = None
    if ready:
        values = crop_labels["yield_kg_per_ha"]
        point = finite_median(values, "yield forecast")
        lower = min(float(value) for value in values)
        upper = max(float(value) for value in values)
        area = float(candidate["season_area_ha"])
        point_quantity = finite_product(point, area, "yield forecast quantity")
        lower_quantity = finite_product(lower, area, "historical minimum quantity")
        upper_quantity = finite_product(upper, area, "historical maximum quantity")

    return {
        "as_of_date": as_of_date.isoformat(),
        "farm_code": candidate["farm_code"],
        "field_code": candidate["field_code"],
        "season_code": candidate["season_code"],
        "crop_code": candidate["crop_code"],
        "model_version": MODEL_VERSION,
        "forecast_status": "ready" if ready else "insufficient_history",
        "forecast_origin_date": origin.date().isoformat(),
        "expected_harvest_date": candidate["expected_harvest_date"].date().isoformat(),
        "season_area_ha": _rounded(float(candidate["season_area_ha"])),
        "history_start_at": _timestamp_min(crop_labels),
        "history_end_at": _timestamp_max(crop_labels),
        "history_seasons": history_count,
        "backtest_origins": backtest.origins,
        "backtest_seasons": backtest.seasons,
        "forecast_yield_kg_per_ha": _rounded(point),
        "observed_min_yield_kg_per_ha": _rounded(lower),
        "observed_max_yield_kg_per_ha": _rounded(upper),
        "forecast_quantity_kg": _rounded(point_quantity),
        "observed_min_quantity_kg": _rounded(lower_quantity),
        "observed_max_quantity_kg": _rounded(upper_quantity),
        "backtest_mae_kg_per_ha": _rounded(backtest.mae if ready else None),
        "backtest_wape_pct": _rounded(backtest.wape_pct if ready else None),
    }


def _timestamp_min(labels: pd.DataFrame) -> str | None:
    if labels.empty:
        return None
    return labels["season_completed_at"].min().isoformat()


def _timestamp_max(labels: pd.DataFrame) -> str | None:
    if labels.empty:
        return None
    return labels["season_completed_at"].max().isoformat()


def _rounded(value: float | None) -> float | None:
    return None if value is None else round(float(value), 6)
