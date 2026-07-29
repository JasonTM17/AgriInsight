from __future__ import annotations

from collections.abc import Iterable
from decimal import Decimal, localcontext
import math
import sys

from agriinsight.inventory_demand_forecast_contract import (
    InventoryDemandForecastError,
)


def finite_sum(values: Iterable[float], label: str) -> float:
    try:
        total = math.fsum(float(value) for value in values)
    except OverflowError as exc:
        raise InventoryDemandForecastError(
            f"{label} exceeds finite numeric range"
        ) from exc
    return finite_nonnegative(total, label)


def finite_mean(values: Iterable[float]) -> float:
    items = tuple(float(value) for value in values)
    if not items:
        return 0.0
    try:
        mean = math.fsum(value / len(items) for value in items)
    except OverflowError as exc:
        raise InventoryDemandForecastError(
            "forecast calculation exceeds finite numeric range"
        ) from exc
    return finite_nonnegative(mean, "forecast calculation")


def finite_product(value: float, multiplier: int, label: str) -> float:
    if value > sys.float_info.max / multiplier:
        raise InventoryDemandForecastError(f"{label} exceeds finite numeric range")
    return finite_nonnegative(value * multiplier, label)


def finite_wape_percent(
    errors: Iterable[float],
    actuals: Iterable[float],
) -> float | None:
    with localcontext() as context:
        context.prec = 50
        error_total = sum((Decimal.from_float(value) for value in errors), Decimal(0))
        actual_total = sum((Decimal.from_float(value) for value in actuals), Decimal(0))
        if actual_total == 0:
            return None
        wape_pct = float(error_total / actual_total * Decimal(100))
    return finite_nonnegative(wape_pct, "backtest WAPE")


def finite_nonnegative(value: float, label: str) -> float:
    if not math.isfinite(value) or value < 0:
        raise InventoryDemandForecastError(f"{label} exceeds finite numeric range")
    return value
