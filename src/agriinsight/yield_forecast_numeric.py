from __future__ import annotations

from collections.abc import Iterable
from decimal import Decimal, localcontext
import math
import sys

from agriinsight.yield_forecast_contract import YieldForecastError


def finite_sum(values: Iterable[float], label: str) -> float:
    try:
        result = math.fsum(float(value) for value in values)
    except OverflowError as exc:
        raise YieldForecastError(f"{label} exceeds finite numeric range") from exc
    return finite_nonnegative(result, label)


def finite_mean(values: Iterable[float], label: str) -> float:
    items = tuple(float(value) for value in values)
    if not items:
        raise YieldForecastError(f"{label} requires at least one value")
    try:
        result = math.fsum(value / len(items) for value in items)
    except OverflowError as exc:
        raise YieldForecastError(f"{label} exceeds finite numeric range") from exc
    return finite_nonnegative(result, label)


def finite_median(values: Iterable[float], label: str) -> float:
    items = sorted(float(value) for value in values)
    if not items:
        raise YieldForecastError(f"{label} requires at least one value")
    middle = len(items) // 2
    if len(items) % 2:
        return finite_nonnegative(items[middle], label)
    return finite_mean((items[middle - 1], items[middle]), label)


def finite_product(left: float, right: float, label: str) -> float:
    if left and right > sys.float_info.max / left:
        raise YieldForecastError(f"{label} exceeds finite numeric range")
    return finite_nonnegative(left * right, label)


def finite_ratio(numerator: float, denominator: float, label: str) -> float:
    if denominator <= 0:
        raise YieldForecastError(f"{label} requires a positive denominator")
    return finite_nonnegative(numerator / denominator, label)


def finite_wape_percent(
    errors: Iterable[float],
    actuals: Iterable[float],
) -> float | None:
    with localcontext() as context:
        context.prec = 50
        error_total = sum(
            (Decimal.from_float(float(value)) for value in errors),
            Decimal(0),
        )
        actual_total = sum(
            (Decimal.from_float(float(value)) for value in actuals),
            Decimal(0),
        )
        if actual_total == 0:
            return None
        result = float(error_total / actual_total * Decimal(100))
    return finite_nonnegative(result, "backtest WAPE")


def finite_nonnegative(value: float, label: str) -> float:
    if not math.isfinite(value) or value < 0:
        raise YieldForecastError(f"{label} exceeds finite numeric range")
    return value
