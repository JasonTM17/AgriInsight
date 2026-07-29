from __future__ import annotations

import math
from datetime import date
from decimal import Decimal
from numbers import Real

import numpy as np
import pandas as pd

from agriinsight.inventory_demand_forecast import FORECAST_COLUMNS, MODEL_VERSION
from agriinsight.metrics_inventory_forecast_error import (
    InventoryDemandForecastGoldError,
)
from agriinsight.metrics_inventory_forecast_temporal_contract import (
    validate_forecast_temporal_evidence,
)


FORECAST_PAIR_COLUMNS = ("warehouse_code", "material_code")
INVENTORY_STATUS_FORECAST_COLUMNS = (
    "forecast_as_of_date",
    "forecast_model_version",
    "forecast_coverage_status",
    "forecast_history_start_date",
    "forecast_history_end_date",
    "forecast_history_days",
    "forecast_nonzero_demand_days",
    "forecast_horizon_days",
    "forecast_quantity",
    "forecast_lower_quantity",
    "forecast_upper_quantity",
    "forecast_backtest_windows",
    "forecast_backtest_mae",
    "forecast_backtest_wape_pct",
    "forecast_days_of_supply",
    "forecast_suggested_order_quantity",
)

FORECAST_TO_STATUS = {
    "as_of_date": "forecast_as_of_date",
    "model_version": "forecast_model_version",
    "forecast_status": "forecast_coverage_status",
    "history_start_date": "forecast_history_start_date",
    "history_end_date": "forecast_history_end_date",
    "history_days": "forecast_history_days",
    "nonzero_demand_days": "forecast_nonzero_demand_days",
    "horizon_days": "forecast_horizon_days",
    "forecast_quantity": "forecast_quantity",
    "lower_quantity": "forecast_lower_quantity",
    "upper_quantity": "forecast_upper_quantity",
    "backtest_windows": "forecast_backtest_windows",
    "backtest_mae": "forecast_backtest_mae",
    "backtest_wape_pct": "forecast_backtest_wape_pct",
}

_REQUIRED_NUMERIC_COLUMNS = (
    "history_days",
    "nonzero_demand_days",
    "horizon_days",
    "forecast_quantity",
    "lower_quantity",
    "upper_quantity",
    "backtest_windows",
)
_NULLABLE_NUMERIC_COLUMNS = ("backtest_mae", "backtest_wape_pct")
_INTEGER_COLUMNS = (
    "history_days",
    "nonzero_demand_days",
    "horizon_days",
    "backtest_windows",
)
_FORECAST_STATUSES = {"ready", "no_demand", "insufficient_history"}


def validate_forecast_gold(forecast: pd.DataFrame, as_of_date: date) -> None:
    if type(as_of_date) is not date:
        raise InventoryDemandForecastGoldError(
            "forecast expected as-of date must be a date"
        )
    if tuple(forecast.columns) != FORECAST_COLUMNS:
        raise InventoryDemandForecastGoldError("forecast Gold schema is invalid")
    if forecast.empty:
        return
    if forecast.duplicated(list(FORECAST_PAIR_COLUMNS)).any():
        raise InventoryDemandForecastGoldError(
            "forecast contains duplicate warehouse/material keys"
        )
    expected_date = as_of_date.isoformat()
    if not forecast["as_of_date"].eq(expected_date).all():
        raise InventoryDemandForecastGoldError("forecast as-of date is stale")
    if not forecast["history_end_date"].eq(expected_date).all():
        raise InventoryDemandForecastGoldError("forecast history end is stale")
    if not forecast["model_version"].eq(MODEL_VERSION).all():
        raise InventoryDemandForecastGoldError("forecast model version is invalid")
    if not forecast["forecast_status"].isin(_FORECAST_STATUSES).all():
        raise InventoryDemandForecastGoldError("forecast status is invalid")
    for column in (*FORECAST_PAIR_COLUMNS, "base_unit"):
        if not forecast[column].map(
            lambda value: isinstance(value, str) and bool(value.strip())
        ).all():
            raise InventoryDemandForecastGoldError(
                "forecast identity values must be non-blank"
            )
    for column in (*_REQUIRED_NUMERIC_COLUMNS, *_NULLABLE_NUMERIC_COLUMNS):
        _validate_numeric_column(
            forecast[column],
            allow_null=column in _NULLABLE_NUMERIC_COLUMNS,
        )
    if any(
        not float(value).is_integer()
        for column in _INTEGER_COLUMNS
        for value in forecast[column]
    ):
        raise InventoryDemandForecastGoldError(
            "forecast count and horizon values must be integers"
        )
    if (
        (forecast["history_days"] <= 0).any()
        or (forecast["history_days"] > 180).any()
        or (
            forecast["nonzero_demand_days"] > forecast["history_days"]
        ).any()
        or not forecast["horizon_days"].eq(30).all()
    ):
        raise InventoryDemandForecastGoldError(
            "forecast history or horizon is invalid"
        )
    validate_forecast_temporal_evidence(forecast, as_of_date)
    if (
        (forecast["lower_quantity"] > forecast["forecast_quantity"]).any()
        or (forecast["forecast_quantity"] > forecast["upper_quantity"]).any()
    ):
        raise InventoryDemandForecastGoldError("forecast range is invalid")
    ready = forecast["forecast_status"] == "ready"
    if (
        forecast.loc[ready, [*_NULLABLE_NUMERIC_COLUMNS]].isna().any().any()
        or (forecast.loc[ready, "backtest_windows"] < 2).any()
    ):
        raise InventoryDemandForecastGoldError(
            "ready forecast lacks backtest evidence"
        )
    no_demand = forecast["forecast_status"] == "no_demand"
    if (
        not forecast.loc[no_demand, "nonzero_demand_days"].eq(0).all()
        or (forecast.loc[~no_demand, "nonzero_demand_days"] <= 0).any()
        or not forecast.loc[
            no_demand,
            [
                "forecast_quantity",
                "lower_quantity",
                "upper_quantity",
                "backtest_windows",
            ],
        ].eq(0).all().all()
        or forecast.loc[
            ~ready,
            [*_NULLABLE_NUMERIC_COLUMNS],
        ].notna().any().any()
    ):
        raise InventoryDemandForecastGoldError(
            "forecast status evidence is inconsistent"
        )


def validate_forecast_matches_status(
    status: pd.DataFrame,
    forecast: pd.DataFrame,
) -> None:
    matched = forecast[
        [*FORECAST_PAIR_COLUMNS, "base_unit"]
    ].merge(
        status[[*FORECAST_PAIR_COLUMNS, "base_unit"]],
        on=list(FORECAST_PAIR_COLUMNS),
        how="left",
        suffixes=("_forecast", "_status"),
        indicator=True,
        validate="one_to_one",
    )
    if not matched["_merge"].eq("both").all():
        raise InventoryDemandForecastGoldError(
            "forecast key does not match inventory status"
        )
    if not matched["base_unit_forecast"].eq(matched["base_unit_status"]).all():
        raise InventoryDemandForecastGoldError(
            "forecast base unit does not match inventory status"
        )


def _validate_numeric_column(values: pd.Series, *, allow_null: bool) -> None:
    present = values[values.notna()]
    if not allow_null and len(present) != len(values):
        raise InventoryDemandForecastGoldError(
            "forecast required values must be finite"
        )
    for value in present:
        if (
            isinstance(value, (bool, np.bool_))
            or not isinstance(value, (Real, Decimal, np.integer, np.floating))
            or not math.isfinite(float(value))
            or float(value) < 0
        ):
            raise InventoryDemandForecastGoldError(
                "forecast numeric values must be finite and non-negative"
            )
