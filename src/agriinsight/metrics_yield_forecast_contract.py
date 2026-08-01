from __future__ import annotations

from datetime import date

import pandas as pd

from agriinsight.metrics_yield_forecast_error import YieldForecastGoldContractError
from agriinsight.metrics_yield_forecast_reconciliation import validate_active_coverage
from agriinsight.metrics_yield_forecast_validation import (
    dates,
    matching_quantity,
    nonnegative_numbers,
    positive_numbers,
    timestamps,
)
from agriinsight.yield_forecast import FORECAST_COLUMNS, MODEL_VERSION
from agriinsight.yield_forecast_input_validation import IDENTIFIER_PATTERN


ACTIVE_SEASON_COLUMNS = (
    "farm_code",
    "field_code",
    "season_code",
    "crop_code",
    "season_start_date",
    "expected_harvest_date",
    "season_area_ha",
    "target_yield_kg",
)
YIELD_FORECAST_GOLD_COLUMNS = (
    *FORECAST_COLUMNS[:10],
    "target_yield_kg",
    *FORECAST_COLUMNS[10:],
)
_METRIC_COLUMNS = (
    "forecast_yield_kg_per_ha",
    "observed_min_yield_kg_per_ha",
    "observed_max_yield_kg_per_ha",
    "forecast_quantity_kg",
    "observed_min_quantity_kg",
    "observed_max_quantity_kg",
    "backtest_mae_kg_per_ha",
    "backtest_wape_pct",
)
_IDENTITY_COLUMNS = ACTIVE_SEASON_COLUMNS[:4]


def validate_yield_forecast_gold(
    forecast: pd.DataFrame,
    as_of_date: date,
    active_seasons: pd.DataFrame,
) -> None:
    """Validate exact forecast grain against canonical eligible active seasons."""

    if type(as_of_date) is not date:
        raise YieldForecastGoldContractError("forecast as-of date is invalid")
    if (
        not isinstance(forecast, pd.DataFrame)
        or tuple(forecast.columns) != YIELD_FORECAST_GOLD_COLUMNS
    ):
        raise YieldForecastGoldContractError("forecast Gold schema is invalid")
    expected = _validated_active_seasons(active_seasons, as_of_date)
    if forecast.empty:
        if expected.empty:
            return
        raise YieldForecastGoldContractError("forecast active-season coverage is incomplete")
    if forecast.duplicated("season_code").any():
        raise YieldForecastGoldContractError("forecast season grain is duplicated")
    if not forecast["as_of_date"].eq(as_of_date.isoformat()).all():
        raise YieldForecastGoldContractError("forecast as-of date is stale")
    if not forecast["model_version"].eq(MODEL_VERSION).all():
        raise YieldForecastGoldContractError("forecast model version is invalid")
    if not forecast["forecast_status"].isin(("ready", "insufficient_history")).all():
        raise YieldForecastGoldContractError("forecast status is invalid")
    _validate_identifiers(forecast)
    _validate_context(forecast, as_of_date)
    _validate_evidence(forecast)
    _validate_quantities(forecast)
    validate_active_coverage(forecast, expected)


def _validated_active_seasons(
    active_seasons: pd.DataFrame,
    as_of_date: date,
) -> pd.DataFrame:
    if (
        not isinstance(active_seasons, pd.DataFrame)
        or tuple(active_seasons.columns) != ACTIVE_SEASON_COLUMNS
        or not active_seasons.columns.is_unique
    ):
        raise YieldForecastGoldContractError("active season schema is invalid")
    if active_seasons.duplicated("season_code").any():
        raise YieldForecastGoldContractError("active season grain is duplicated")
    _validate_identifiers(active_seasons)
    start = dates(active_seasons["season_start_date"], "active season start")
    expected = dates(
        active_seasons["expected_harvest_date"], "active season expected harvest"
    )
    if (start > as_of_date).any() or (expected <= as_of_date).any() or (start >= expected).any():
        raise YieldForecastGoldContractError("active season chronology is invalid")
    positive_numbers(active_seasons["season_area_ha"], "active season area")
    nonnegative_numbers(
        active_seasons["target_yield_kg"],
        "active season target yield",
        allow_null=True,
    )
    return active_seasons.copy()


def _validate_identifiers(frame: pd.DataFrame) -> None:
    for column in _IDENTITY_COLUMNS:
        if column not in frame or not frame[column].map(
            lambda value: isinstance(value, str)
            and IDENTIFIER_PATTERN.fullmatch(value) is not None
        ).all():
            raise YieldForecastGoldContractError("forecast identity is invalid")


def _validate_context(forecast: pd.DataFrame, as_of_date: date) -> None:
    origins = dates(forecast["forecast_origin_date"], "forecast origin")
    expected = dates(forecast["expected_harvest_date"], "forecast expected harvest")
    if (origins > as_of_date).any() or (expected <= as_of_date).any() or (origins >= expected).any():
        raise YieldForecastGoldContractError("forecast origin chronology is invalid")
    positive_numbers(forecast["season_area_ha"], "forecast season area")
    nonnegative_numbers(
        forecast["target_yield_kg"],
        "forecast target yield",
        allow_null=True,
    )
    counts = ("history_seasons", "backtest_origins", "backtest_seasons")
    for column in counts:
        values = nonnegative_numbers(forecast[column], column)
        if not values.map(lambda value: float(value).is_integer()).all():
            raise YieldForecastGoldContractError("forecast evidence counts are invalid")
    starts = timestamps(forecast["history_start_at"], "forecast history start")
    ends = timestamps(forecast["history_end_at"], "forecast history end")
    has_history = forecast["history_seasons"].astype(float) > 0
    if (
        starts[has_history].isna().any()
        or ends[has_history].isna().any()
        or starts[~has_history].notna().any()
        or ends[~has_history].notna().any()
        or (starts[has_history] > ends[has_history]).any()
        or (ends[has_history] >= origins[has_history]).any()
    ):
        raise YieldForecastGoldContractError("forecast history chronology is invalid")


def _validate_evidence(forecast: pd.DataFrame) -> None:
    for column in _METRIC_COLUMNS:
        nonnegative_numbers(forecast[column], column, allow_null=True)
    ready = forecast["forecast_status"].eq("ready")
    if (
        (forecast.loc[ready, "history_seasons"].astype(float) < 5).any()
        or (forecast.loc[ready, "backtest_origins"].astype(float) < 2).any()
        or (forecast.loc[ready, "backtest_seasons"].astype(float) < 2).any()
        or forecast.loc[ready, list(_METRIC_COLUMNS)].isna().any().any()
        or forecast.loc[~ready, list(_METRIC_COLUMNS)].notna().any().any()
    ):
        raise YieldForecastGoldContractError("forecast status evidence is inconsistent")
    if (
        (forecast["observed_min_yield_kg_per_ha"] > forecast["forecast_yield_kg_per_ha"]).any()
        or (forecast["forecast_yield_kg_per_ha"] > forecast["observed_max_yield_kg_per_ha"]).any()
        or (forecast["observed_min_quantity_kg"] > forecast["forecast_quantity_kg"]).any()
        or (forecast["forecast_quantity_kg"] > forecast["observed_max_quantity_kg"]).any()
    ):
        raise YieldForecastGoldContractError("forecast range is invalid")


def _validate_quantities(forecast: pd.DataFrame) -> None:
    ready = forecast["forecast_status"].eq("ready")
    for _, row in forecast.loc[ready].iterrows():
        area = float(row["season_area_ha"])
        for yield_column, quantity_column in (
            ("forecast_yield_kg_per_ha", "forecast_quantity_kg"),
            ("observed_min_yield_kg_per_ha", "observed_min_quantity_kg"),
            ("observed_max_yield_kg_per_ha", "observed_max_quantity_kg"),
        ):
            if not matching_quantity(row[quantity_column], row[yield_column], area):
                raise YieldForecastGoldContractError("forecast quantity is inconsistent")
