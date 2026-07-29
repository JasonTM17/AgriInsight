from __future__ import annotations

import math
import sqlite3
from datetime import date, timedelta
from decimal import Decimal, localcontext
from numbers import Real

import numpy as np
import pandas as pd

from agriinsight.inventory_demand_forecast import (
    forecast_inventory_demand,
)
from agriinsight.metrics_inventory_forecast_contract import (
    FORECAST_PAIR_COLUMNS,
    FORECAST_TO_STATUS,
    INVENTORY_STATUS_FORECAST_COLUMNS,
    InventoryDemandForecastGoldError,
    validate_forecast_gold,
    validate_forecast_matches_status,
)
from agriinsight.metrics_inventory_forecast_status_contract import (
    validate_inventory_forecast_status,
)


def build_inventory_demand_forecast_gold(
    connection: sqlite3.Connection,
    as_of_date: date,
) -> pd.DataFrame:
    """Build the bounded, versioned forecast Gold frame from warehouse facts."""

    if type(as_of_date) is not date:
        raise InventoryDemandForecastGoldError("as_of_date must be a date")
    window_start = as_of_date - timedelta(days=179)
    movements = pd.read_sql_query(
        """
        SELECT t.transaction_date,
               w.warehouse_code,
               m.material_code,
               t.base_unit,
               t.transaction_type,
               t.quantity_base_unit
        FROM fact_inventory_transaction t
        JOIN dim_warehouse w USING (warehouse_key)
        JOIN dim_material m USING (material_key)
        WHERE t.transaction_date >= ?
          AND t.transaction_date <= ?
        ORDER BY w.warehouse_code,
                 m.material_code,
                 t.transaction_date,
                 t.transaction_id
        """,
        connection,
        params=(window_start.isoformat(), as_of_date.isoformat()),
    )
    forecast = forecast_inventory_demand(movements, as_of_date)
    validate_forecast_gold(forecast, as_of_date)
    return forecast


def attach_inventory_demand_forecast(
    inventory_status: pd.DataFrame,
    forecast: pd.DataFrame,
    as_of_date: date,
) -> pd.DataFrame:
    """Attach decision evidence without changing current inventory policy."""

    if type(as_of_date) is not date:
        raise InventoryDemandForecastGoldError("as_of_date must be a date")
    _validate_inventory_status(inventory_status)
    validate_forecast_gold(forecast, as_of_date)

    status = inventory_status.copy()
    if not forecast.empty:
        validate_forecast_matches_status(status, forecast)
        evidence = forecast[
            [*FORECAST_PAIR_COLUMNS, *FORECAST_TO_STATUS]
        ].rename(columns=FORECAST_TO_STATUS)
        status = status.merge(
            evidence,
            on=list(FORECAST_PAIR_COLUMNS),
            how="left",
            sort=False,
            validate="one_to_one",
        )
    else:
        for column in INVENTORY_STATUS_FORECAST_COLUMNS[:-2]:
            status[column] = None

    status["forecast_coverage_status"] = status[
        "forecast_coverage_status"
    ].fillna("unavailable")
    status["forecast_days_of_supply"] = status.apply(
        _forecast_days_of_supply,
        axis=1,
    )
    status["forecast_suggested_order_quantity"] = status.apply(
        _forecast_suggested_order_quantity,
        axis=1,
    )
    status = status.sort_values(
        list(FORECAST_PAIR_COLUMNS),
        kind="stable",
    ).reset_index(drop=True)
    result = status[
        [
            *(
                column
                for column in inventory_status.columns
                if column not in INVENTORY_STATUS_FORECAST_COLUMNS
            ),
            *INVENTORY_STATUS_FORECAST_COLUMNS,
        ]
    ]
    validate_inventory_forecast_status(result, as_of_date.isoformat())
    return result


def _validate_inventory_status(status: pd.DataFrame) -> None:
    required = {
        *FORECAST_PAIR_COLUMNS,
        "base_unit",
        "stock_quantity",
    }
    if not status.columns.is_unique:
        raise InventoryDemandForecastGoldError(
            "inventory status column names must be unique"
        )
    if not required.issubset(status.columns):
        raise InventoryDemandForecastGoldError(
            "inventory status is missing forecast join columns"
        )
    if set(status.columns) & set(INVENTORY_STATUS_FORECAST_COLUMNS):
        raise InventoryDemandForecastGoldError(
            "inventory status already contains forecast evidence"
        )
    if status.duplicated(list(FORECAST_PAIR_COLUMNS)).any():
        raise InventoryDemandForecastGoldError(
            "inventory status contains duplicate warehouse/material keys"
        )
    for column in (*FORECAST_PAIR_COLUMNS, "base_unit"):
        if not status[column].map(
            lambda value: isinstance(value, str) and bool(value.strip())
        ).all():
            raise InventoryDemandForecastGoldError(
                "inventory status keys and base unit must be non-blank"
            )
    for value in status["stock_quantity"]:
        if (
            isinstance(value, (bool, np.bool_))
            or not isinstance(value, (Real, Decimal, np.integer, np.floating))
            or not math.isfinite(float(value))
        ):
            raise InventoryDemandForecastGoldError(
                "inventory status stock values must be finite"
            )


def _forecast_days_of_supply(row: pd.Series) -> float:
    point = row.get("forecast_quantity")
    stock = row.get("stock_quantity")
    horizon = row.get("forecast_horizon_days")
    if pd.isna(point) or pd.isna(stock) or pd.isna(horizon):
        return float("nan")
    if float(point) <= 0 or float(stock) <= 0:
        return float("nan")
    with localcontext() as context:
        context.prec = 50
        result = float(
            Decimal.from_float(float(stock))
            * Decimal.from_float(float(horizon))
            / Decimal.from_float(float(point))
        )
    return _finite_derived(result)


def _forecast_suggested_order_quantity(row: pd.Series) -> float:
    upper = row.get("forecast_upper_quantity")
    stock = row.get("stock_quantity")
    if pd.isna(upper) or pd.isna(stock):
        return float("nan")
    return _finite_derived(max(float(upper) - max(float(stock), 0.0), 0.0))


def _finite_derived(value: float) -> float:
    if not math.isfinite(value) or value < 0:
        raise InventoryDemandForecastGoldError(
            "forecast derived values must be finite and non-negative"
        )
    return value
