from __future__ import annotations

from datetime import date, datetime
import math

import numpy as np
import pandas as pd

from agriinsight.metrics_yield_forecast_error import YieldForecastGoldContractError
from agriinsight.yield_forecast_input_validation import (
    ISO_DATE_PATTERN,
    ISO_TIMESTAMP_PATTERN,
)


def dates(values: pd.Series, label: str) -> pd.Series:
    def parse(value: object) -> date:
        if not isinstance(value, str) or ISO_DATE_PATTERN.fullmatch(value) is None:
            raise YieldForecastGoldContractError(f"{label} is invalid")
        try:
            return date.fromisoformat(value)
        except ValueError as error:
            raise YieldForecastGoldContractError(f"{label} is invalid") from error

    return values.map(parse)


def timestamps(values: pd.Series, label: str) -> pd.Series:
    def parse(value: object) -> pd.Timestamp:
        if _is_missing(value):
            return pd.NaT
        if isinstance(value, str):
            if ISO_TIMESTAMP_PATTERN.fullmatch(value) is None:
                raise YieldForecastGoldContractError(f"{label} is invalid")
            try:
                value = datetime.fromisoformat(value)
            except ValueError as error:
                raise YieldForecastGoldContractError(f"{label} is invalid") from error
        if not isinstance(value, (datetime, pd.Timestamp, np.datetime64)):
            raise YieldForecastGoldContractError(f"{label} is invalid")
        try:
            parsed = pd.Timestamp(value)
        except (OverflowError, TypeError, ValueError) as error:
            raise YieldForecastGoldContractError(f"{label} is invalid") from error
        if pd.isna(parsed) or parsed.tzinfo is not None:
            raise YieldForecastGoldContractError(f"{label} is invalid")
        return parsed

    try:
        return values.map(parse).astype("datetime64[ns]")
    except (OverflowError, TypeError, ValueError) as error:
        raise YieldForecastGoldContractError(f"{label} is invalid") from error


def _is_missing(value: object) -> bool:
    missing = pd.isna(value)
    return isinstance(missing, (bool, np.bool_)) and bool(missing)


def positive_numbers(values: pd.Series, label: str) -> pd.Series:
    parsed = nonnegative_numbers(values, label)
    if (parsed <= 0).any():
        raise YieldForecastGoldContractError(f"{label} must be positive")
    return parsed


def nonnegative_numbers(
    values: pd.Series,
    label: str,
    *,
    allow_null: bool = False,
) -> pd.Series:
    parsed = pd.to_numeric(values, errors="coerce")
    present = values.notna()
    if (
        (not allow_null and (~present).any())
        or parsed[present].isna().any()
        or not np.isfinite(parsed[present].to_numpy(dtype=float)).all()
        or (parsed[present] < 0).any()
        or values[present].map(lambda value: isinstance(value, (bool, np.bool_))).any()
    ):
        raise YieldForecastGoldContractError(f"{label} must be finite")
    return parsed


def matching_quantity(actual: object, yield_value: object, area: object) -> bool:
    expected = round(float(yield_value) * float(area), 6)
    return math.isclose(float(actual), expected, abs_tol=1e-6)
