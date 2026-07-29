from __future__ import annotations

from datetime import date

import numpy as np
import pandas as pd

from agriinsight.inventory_demand_forecast_contract import (
    InventoryDemandForecastError,
    validated_movements,
)
from agriinsight.inventory_demand_forecast_numeric import (
    finite_mean,
    finite_nonnegative,
    finite_product,
    finite_sum,
    finite_wape_percent,
)


MODEL_VERSION = "mean-daily-usage-90d-v1"
HISTORY_WINDOW_DAYS = 180
FORECAST_LOOKBACK_DAYS = 90
FORECAST_HORIZON_DAYS = 30
BACKTEST_STEP_DAYS = 7
MIN_BACKTEST_WINDOWS = 2

FORECAST_COLUMNS = (
    "as_of_date",
    "warehouse_code",
    "material_code",
    "base_unit",
    "model_version",
    "forecast_status",
    "history_start_date",
    "history_end_date",
    "history_days",
    "nonzero_demand_days",
    "horizon_days",
    "forecast_quantity",
    "lower_quantity",
    "upper_quantity",
    "backtest_windows",
    "backtest_mae",
    "backtest_wape_pct",
)

def forecast_inventory_demand(
    movements: pd.DataFrame,
    as_of_date: date,
) -> pd.DataFrame:
    """Build deterministic 30-day demand baselines from canonical movements."""

    if type(as_of_date) is not date:
        raise InventoryDemandForecastError("as_of_date must be a date")
    cutoff = pd.Timestamp(as_of_date)
    window_start = cutoff - pd.Timedelta(days=HISTORY_WINDOW_DAYS - 1)
    facts = validated_movements(
        movements,
        window_start=window_start,
        cutoff=cutoff,
    )
    if facts.empty:
        return pd.DataFrame(columns=FORECAST_COLUMNS)

    unit_counts = facts.groupby(
        ["warehouse_code", "material_code"], sort=False
    )["base_unit"].nunique()
    if bool((unit_counts > 1).any()):
        raise InventoryDemandForecastError(
            "each warehouse/material forecast requires a single base unit"
        )

    records = [
        _forecast_group(group, as_of_date)
        for _, group in facts.groupby(
            ["warehouse_code", "material_code", "base_unit"], sort=True
        )
    ]
    return (
        pd.DataFrame.from_records(records, columns=FORECAST_COLUMNS)
        .sort_values(["warehouse_code", "material_code"], kind="stable")
        .reset_index(drop=True)
    )


def _forecast_group(group: pd.DataFrame, as_of_date: date) -> dict[str, object]:
    history_start = group["transaction_date"].min()
    daily_index = pd.date_range(history_start, pd.Timestamp(as_of_date), freq="D")
    out_facts = group.loc[group["transaction_type"] == "OUT"]
    daily_totals = {
        transaction_date: finite_sum(
            day_rows["quantity_base_unit"],
            "daily OUT demand",
        )
        for transaction_date, day_rows in out_facts.groupby(
            "transaction_date", sort=True
        )
    }
    daily_out = pd.Series(daily_totals, dtype=float).reindex(
        daily_index,
        fill_value=0.0,
    )

    forecast_quantity = finite_product(
        finite_mean(daily_out.tail(FORECAST_LOOKBACK_DAYS)),
        FORECAST_HORIZON_DAYS,
        "forecast quantity",
    )
    complete_totals = [
        finite_sum(
            daily_out.iloc[start : start + FORECAST_HORIZON_DAYS],
            "rolling 30-day demand",
        )
        for start in range(len(daily_out) - FORECAST_HORIZON_DAYS + 1)
    ]
    if not complete_totals:
        lower_quantity = forecast_quantity
        upper_quantity = forecast_quantity
    else:
        lower_quantity = finite_nonnegative(
            float(np.quantile(complete_totals, 0.10)),
            "lower forecast range",
        )
        upper_quantity = finite_nonnegative(
            float(np.quantile(complete_totals, 0.90)),
            "upper forecast range",
        )
    lower_quantity = min(max(0.0, lower_quantity), forecast_quantity)
    upper_quantity = max(forecast_quantity, upper_quantity)

    nonzero_days = int((daily_out > 0).sum())
    backtest_windows, backtest_mae, backtest_wape_pct = _backtest(daily_out)
    if nonzero_days == 0:
        status = "no_demand"
        backtest_windows, backtest_mae, backtest_wape_pct = 0, None, None
    elif (
        backtest_windows < MIN_BACKTEST_WINDOWS
        or backtest_mae is None
        or backtest_wape_pct is None
    ):
        status = "insufficient_history"
        backtest_mae, backtest_wape_pct = None, None
    else:
        status = "ready"

    first = group.iloc[0]
    return {
        "as_of_date": as_of_date.isoformat(),
        "warehouse_code": first["warehouse_code"],
        "material_code": first["material_code"],
        "base_unit": first["base_unit"],
        "model_version": MODEL_VERSION,
        "forecast_status": status,
        "history_start_date": history_start.date().isoformat(),
        "history_end_date": as_of_date.isoformat(),
        "history_days": len(daily_out),
        "nonzero_demand_days": nonzero_days,
        "horizon_days": FORECAST_HORIZON_DAYS,
        "forecast_quantity": _rounded(forecast_quantity),
        "lower_quantity": _rounded(lower_quantity),
        "upper_quantity": _rounded(upper_quantity),
        "backtest_windows": backtest_windows,
        "backtest_mae": _rounded(backtest_mae),
        "backtest_wape_pct": _rounded(backtest_wape_pct),
    }


def _backtest(daily_out: pd.Series) -> tuple[int, float | None, float | None]:
    predictions: list[float] = []
    actuals: list[float] = []
    last_origin = len(daily_out) - FORECAST_HORIZON_DAYS
    for origin in range(
        FORECAST_LOOKBACK_DAYS,
        last_origin + 1,
        BACKTEST_STEP_DAYS,
    ):
        training = daily_out.iloc[origin - FORECAST_LOOKBACK_DAYS : origin]
        actual = daily_out.iloc[origin : origin + FORECAST_HORIZON_DAYS]
        predictions.append(
            finite_product(
                finite_mean(training),
                FORECAST_HORIZON_DAYS,
                "backtest prediction",
            )
        )
        actuals.append(finite_sum(actual, "backtest actual demand"))

    if not predictions:
        return 0, None, None
    errors = [abs(prediction - actual) for prediction, actual in zip(predictions, actuals)]
    mae = finite_mean(errors)
    wape_pct = finite_wape_percent(errors, actuals)
    return len(predictions), mae, wape_pct


def _rounded(value: float | None) -> float | None:
    return None if value is None else round(float(value), 6)
