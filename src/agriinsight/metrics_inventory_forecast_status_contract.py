from __future__ import annotations

from decimal import Decimal, localcontext
import math
from numbers import Real

import numpy as np
import pandas as pd

from agriinsight.inventory_demand_forecast import FORECAST_COLUMNS
from agriinsight.metrics_inventory_forecast_contract import (
    FORECAST_PAIR_COLUMNS,
    FORECAST_TO_STATUS,
    INVENTORY_STATUS_FORECAST_COLUMNS,
    InventoryDemandForecastGoldError,
    validate_forecast_gold,
)
from agriinsight.metrics_inventory_forecast_temporal_contract import (
    strict_iso_date,
)


_COVERAGE_STATUSES = {
    "ready",
    "no_demand",
    "insufficient_history",
    "unavailable",
}
_STATUS_IDENTITY_COLUMNS = (
    *FORECAST_PAIR_COLUMNS,
    "base_unit",
    "stock_quantity",
)
# CSV round-tripping can change the final binary digit. Bound tolerance by two
# representable float steps instead of a relative percentage that grows with
# the business quantity.
_DERIVED_ULP_MULTIPLIER = 2
_DERIVED_ABSOLUTE_TOLERANCE = 1e-9


def validate_inventory_forecast_status(
    status: pd.DataFrame,
    expected_as_of_date: str,
) -> None:
    """Validate joined forecast evidence at generation and snapshot boundaries."""

    if not isinstance(status, pd.DataFrame) or not status.columns.is_unique:
        raise InventoryDemandForecastGoldError(
            "inventory status forecast schema is invalid"
        )
    required = {
        *INVENTORY_STATUS_FORECAST_COLUMNS,
        *_STATUS_IDENTITY_COLUMNS,
    }
    if not required.issubset(status.columns):
        raise InventoryDemandForecastGoldError(
            "inventory status forecast schema is incomplete"
        )
    as_of_date = strict_iso_date(expected_as_of_date, "expected as-of")
    if status.duplicated(list(FORECAST_PAIR_COLUMNS)).any():
        raise InventoryDemandForecastGoldError(
            "inventory status contains duplicate forecast keys"
        )
    for column in (*FORECAST_PAIR_COLUMNS, "base_unit"):
        if not status[column].map(
            lambda value: isinstance(value, str) and bool(value.strip())
        ).all():
            raise InventoryDemandForecastGoldError(
                "inventory status forecast identity is invalid"
            )
    _validate_stock(status["stock_quantity"])

    coverage = status["forecast_coverage_status"]
    if not coverage.isin(_COVERAGE_STATUSES).all():
        raise InventoryDemandForecastGoldError(
            "inventory status forecast coverage is invalid"
        )
    available = coverage != "unavailable"
    evidence_columns = [
        column
        for column in INVENTORY_STATUS_FORECAST_COLUMNS
        if column != "forecast_coverage_status"
    ]
    if status.loc[~available, evidence_columns].notna().any().any():
        raise InventoryDemandForecastGoldError(
            "unavailable forecast must not contain decision evidence"
        )
    if not available.any():
        return

    available_rows = status.loc[available]
    forecast = _forecast_projection(available_rows)
    validate_forecast_gold(forecast, as_of_date)
    _validate_derived_decision_evidence(available_rows)


def _forecast_projection(available_rows: pd.DataFrame) -> pd.DataFrame:
    status_columns = [
        *FORECAST_PAIR_COLUMNS,
        "base_unit",
        *FORECAST_TO_STATUS.values(),
    ]
    reverse_names = {
        status_column: forecast_column
        for forecast_column, status_column in FORECAST_TO_STATUS.items()
    }
    projected = available_rows.loc[:, status_columns].rename(
        columns=reverse_names
    )
    return projected.loc[:, list(FORECAST_COLUMNS)]


def _validate_derived_decision_evidence(
    available_rows: pd.DataFrame,
) -> None:
    for row in available_rows.itertuples(index=False):
        stock = float(row.stock_quantity)
        point = float(row.forecast_quantity)
        upper = float(row.forecast_upper_quantity)
        horizon = float(row.forecast_horizon_days)

        expected_suggested = max(upper - max(stock, 0.0), 0.0)
        suggested = _finite_nonnegative(
            row.forecast_suggested_order_quantity,
            "suggested order quantity",
        )
        if (
            not math.isfinite(expected_suggested)
            or not _matches_derived(suggested, expected_suggested)
        ):
            raise InventoryDemandForecastGoldError(
                "forecast suggested order quantity is inconsistent"
            )

        if stock <= 0 or point <= 0:
            if not pd.isna(row.forecast_days_of_supply):
                raise InventoryDemandForecastGoldError(
                    "forecast days of supply nullability is inconsistent"
                )
            continue

        with localcontext() as context:
            context.prec = 50
            expected_supply = float(
                Decimal.from_float(stock)
                * Decimal.from_float(horizon)
                / Decimal.from_float(point)
            )
        supply = _finite_nonnegative(
            row.forecast_days_of_supply,
            "days of supply",
        )
        if (
            not math.isfinite(expected_supply)
            or not _matches_derived(supply, expected_supply)
        ):
            raise InventoryDemandForecastGoldError(
                "forecast days of supply is inconsistent"
            )


def _validate_stock(values: pd.Series) -> None:
    for value in values:
        if (
            isinstance(value, (bool, np.bool_))
            or not isinstance(value, (Real, Decimal, np.integer, np.floating))
            or not math.isfinite(float(value))
        ):
            raise InventoryDemandForecastGoldError(
                "inventory status stock values must be finite"
            )


def _matches_derived(actual: float, expected: float) -> bool:
    tolerance = max(
        _DERIVED_ABSOLUTE_TOLERANCE,
        _DERIVED_ULP_MULTIPLIER * math.ulp(expected),
    )
    return abs(actual - expected) <= tolerance


def _finite_nonnegative(value: object, label: str) -> float:
    if (
        pd.isna(value)
        or isinstance(value, (bool, np.bool_))
        or not isinstance(value, (Real, Decimal, np.integer, np.floating))
    ):
        raise InventoryDemandForecastGoldError(
            f"forecast {label} must be finite"
        )
    numeric = float(value)
    if not math.isfinite(numeric) or numeric < 0:
        raise InventoryDemandForecastGoldError(
            f"forecast {label} must be finite"
        )
    return numeric
