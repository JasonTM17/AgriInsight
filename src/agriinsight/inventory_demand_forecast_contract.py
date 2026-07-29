from __future__ import annotations

from datetime import date, datetime
from decimal import Decimal
from numbers import Real
import re

import numpy as np
import pandas as pd


INPUT_COLUMNS = (
    "transaction_date",
    "warehouse_code",
    "material_code",
    "base_unit",
    "transaction_type",
    "quantity_base_unit",
)

_IDENTIFIER_COLUMNS = ("warehouse_code", "material_code", "base_unit")
_ISO_DATE_PATTERN = re.compile(r"^\d{4}-\d{2}-\d{2}$")


class InventoryDemandForecastError(ValueError):
    """Raised when warehouse movement facts violate the forecast contract."""


def validated_movements(
    movements: pd.DataFrame,
    *,
    window_start: pd.Timestamp,
    cutoff: pd.Timestamp,
) -> pd.DataFrame:
    """Return strictly validated facts inside the requested forecast window.

    Dates are validated before filtering because an invalid date cannot be
    classified safely. Other fields are validated after filtering so facts
    outside the historical boundary cannot influence an earlier forecast.
    """

    if not isinstance(movements, pd.DataFrame):
        raise InventoryDemandForecastError("movements must be a pandas DataFrame")
    if not movements.columns.is_unique:
        raise InventoryDemandForecastError("movement column names must be unique")
    missing = [column for column in INPUT_COLUMNS if column not in movements.columns]
    if missing:
        raise InventoryDemandForecastError(
            f"movements are missing required columns: {', '.join(missing)}"
        )

    facts = movements.loc[:, INPUT_COLUMNS].copy()
    if facts.empty:
        return facts

    facts["transaction_date"] = _validated_dates(facts["transaction_date"])
    facts = facts[
        facts["transaction_date"].between(window_start, cutoff, inclusive="both")
    ].copy()
    if facts.empty:
        return facts

    for column in _IDENTIFIER_COLUMNS:
        valid = facts[column].map(
            lambda value: isinstance(value, str) and bool(value.strip())
        )
        if not bool(valid.all()):
            raise InventoryDemandForecastError(
                "warehouse, material, and base unit must be non-blank identifiers"
            )
        facts[column] = facts[column].str.strip()

    transaction_types = facts["transaction_type"]
    if not bool(transaction_types.map(lambda value: isinstance(value, str)).all()):
        raise InventoryDemandForecastError("transaction_type must be IN or OUT")
    facts["transaction_type"] = transaction_types.str.strip()
    if not bool(facts["transaction_type"].isin(("IN", "OUT")).all()):
        raise InventoryDemandForecastError("transaction_type must be IN or OUT")

    facts["quantity_base_unit"] = _validated_quantities(
        facts["quantity_base_unit"]
    )
    return facts


def _validated_quantities(values: pd.Series) -> pd.Series:
    def parse_quantity(value: object) -> float:
        numeric_types = (Real, Decimal, np.integer, np.floating)
        if isinstance(value, (bool, np.bool_)) or not isinstance(
            value, numeric_types
        ):
            raise InventoryDemandForecastError(
                "quantity_base_unit values must be non-negative finite numbers"
            )
        try:
            quantity = float(value)
        except (OverflowError, TypeError, ValueError) as exc:
            raise InventoryDemandForecastError(
                "quantity_base_unit values must be non-negative finite numbers"
            ) from exc
        if not np.isfinite(quantity) or quantity < 0:
            raise InventoryDemandForecastError(
                "quantity_base_unit values must be non-negative finite numbers"
            )
        return quantity

    try:
        return values.map(parse_quantity).astype(float)
    except InventoryDemandForecastError:
        raise
    except (OverflowError, TypeError, ValueError) as exc:
        raise InventoryDemandForecastError(
            "quantity_base_unit values must be non-negative finite numbers"
        ) from exc


def _validated_dates(values: pd.Series) -> pd.Series:
    def parse_date(value: object) -> pd.Timestamp:
        allowed_types = (str, date, datetime, pd.Timestamp, np.datetime64)
        if isinstance(value, (bool, np.bool_)) or not isinstance(value, allowed_types):
            raise InventoryDemandForecastError(
                "transaction_date values must be valid timezone-free dates"
            )
        try:
            if isinstance(value, str):
                if not _ISO_DATE_PATTERN.fullmatch(value):
                    raise ValueError("date string must use YYYY-MM-DD")
                parsed_date = pd.Timestamp(date.fromisoformat(value))
            else:
                parsed_date = pd.Timestamp(value)
        except (AttributeError, OverflowError, TypeError, ValueError) as exc:
            raise InventoryDemandForecastError(
                "transaction_date values must be valid timezone-free dates"
            ) from exc
        if (
            pd.isna(parsed_date)
            or parsed_date.tzinfo is not None
            or parsed_date != parsed_date.normalize()
        ):
            raise InventoryDemandForecastError(
                "transaction_date values must be valid timezone-free dates"
            )
        return parsed_date.normalize()

    try:
        return values.map(parse_date).astype("datetime64[ns]")
    except InventoryDemandForecastError:
        raise
    except (AttributeError, OverflowError, TypeError, ValueError) as exc:
        raise InventoryDemandForecastError(
            "transaction_date values must be valid timezone-free dates"
        ) from exc
