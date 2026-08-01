from __future__ import annotations

import math

import pandas as pd

from agriinsight.metrics_yield_forecast_error import YieldForecastGoldContractError


def validate_active_coverage(
    forecast: pd.DataFrame,
    expected: pd.DataFrame,
) -> None:
    """Require Gold rows to preserve every canonical active season context."""

    actual = forecast.loc[
        :,
        (
            "farm_code",
            "field_code",
            "season_code",
            "crop_code",
            "forecast_origin_date",
            "expected_harvest_date",
            "season_area_ha",
            "target_yield_kg",
        ),
    ].rename(columns={"forecast_origin_date": "season_start_date"})
    if set(actual["season_code"]) != set(expected["season_code"]):
        raise YieldForecastGoldContractError("forecast active-season coverage is incomplete")
    joined = actual.merge(
        expected,
        on="season_code",
        suffixes=("_actual", "_expected"),
        validate="one_to_one",
    )
    for column in (
        "farm_code",
        "field_code",
        "crop_code",
        "season_start_date",
        "expected_harvest_date",
    ):
        if not joined[f"{column}_actual"].eq(joined[f"{column}_expected"]).all():
            raise YieldForecastGoldContractError("forecast season relationship is invalid")
    if not all(
        math.isclose(float(actual_value), float(expected_value), abs_tol=1e-6)
        for actual_value, expected_value in zip(
            joined["season_area_ha_actual"],
            joined["season_area_ha_expected"],
            strict=True,
        )
    ):
        raise YieldForecastGoldContractError("forecast season area is invalid")
    if not all(
        _matching_nullable_number(actual_value, expected_value)
        for actual_value, expected_value in zip(
            joined["target_yield_kg_actual"],
            joined["target_yield_kg_expected"],
            strict=True,
        )
    ):
        raise YieldForecastGoldContractError("forecast target yield is invalid")


def _matching_nullable_number(actual: object, expected: object) -> bool:
    if pd.isna(actual) or pd.isna(expected):
        return pd.isna(actual) and pd.isna(expected)
    return math.isclose(float(actual), float(expected), abs_tol=1e-6)
