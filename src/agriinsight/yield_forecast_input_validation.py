from __future__ import annotations

from datetime import date, datetime
from decimal import Decimal
from numbers import Real
import re

import numpy as np
import pandas as pd


IDENTIFIER_PATTERN = re.compile(r"^[A-Z0-9][A-Z0-9_-]{0,63}$")
ISO_DATE_PATTERN = re.compile(r"^\d{4}-\d{2}-\d{2}$")
ISO_TIMESTAMP_PATTERN = re.compile(
    r"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,6})?$"
)


class YieldForecastError(ValueError):
    """Raised when yield facts violate the deterministic forecast contract."""


def required_frame(
    frame: pd.DataFrame,
    columns: tuple[str, ...],
    label: str,
) -> pd.DataFrame:
    if not isinstance(frame, pd.DataFrame):
        raise YieldForecastError(f"{label} must be a pandas DataFrame")
    if not frame.columns.is_unique:
        raise YieldForecastError(f"{label} column names must be unique")
    missing = [column for column in columns if column not in frame.columns]
    if missing:
        raise YieldForecastError(
            f"{label} is missing required columns: {', '.join(missing)}"
        )
    return frame.loc[:, columns].copy()


def validate_identifiers(
    frame: pd.DataFrame,
    columns: tuple[str, ...],
) -> None:
    for column in columns:
        valid = frame[column].map(
            lambda value: isinstance(value, str)
            and IDENTIFIER_PATTERN.fullmatch(value) is not None
        )
        if not bool(valid.all()):
            raise YieldForecastError(
                f"{column} values must be canonical identifiers"
            )


def validate_status(frame: pd.DataFrame, expected: str) -> None:
    if not bool(frame["season_status"].eq(expected).fillna(False).all()):
        raise YieldForecastError(
            f"season_status must be {expected} for this forecast input"
        )


def validate_season_context(facts: pd.DataFrame) -> None:
    context_columns = (
        "farm_code",
        "field_code",
        "crop_code",
        "season_start_date",
        "season_completed_at",
        "season_area_ha",
        "season_status",
    )
    context_counts = facts.groupby("season_code", sort=False)[
        list(context_columns)
    ].nunique(dropna=False)
    if bool((context_counts > 1).any(axis=None)):
        raise YieldForecastError(
            "harvest events for a season require one consistent season context"
        )


def numbers(values: pd.Series, *, positive: bool, label: str) -> pd.Series:
    def parse(value: object) -> float:
        numeric_types = (Real, Decimal, np.integer, np.floating)
        adjective = "positive" if positive else "non-negative"
        if isinstance(value, (bool, np.bool_)) or not isinstance(
            value, numeric_types
        ):
            raise YieldForecastError(
                f"{label} values must be {adjective} finite numbers"
            )
        try:
            result = float(value)
        except (OverflowError, TypeError, ValueError) as exc:
            raise YieldForecastError(f"{label} values must be finite numbers") from exc
        if not np.isfinite(result) or result < 0 or (positive and result <= 0):
            raise YieldForecastError(
                f"{label} values must be {adjective} finite numbers"
            )
        return result

    return values.map(parse).astype(float)


def dates(values: pd.Series) -> pd.Series:
    def parse(value: object) -> pd.Timestamp:
        if isinstance(value, str):
            if not ISO_DATE_PATTERN.fullmatch(value):
                raise YieldForecastError(
                    "date values must be valid timezone-free dates"
                )
            try:
                value = date.fromisoformat(value)
            except ValueError as exc:
                raise YieldForecastError(
                    "date values must be valid timezone-free dates"
                ) from exc
        if isinstance(value, pd.Timestamp):
            pass
        elif isinstance(value, datetime) or not isinstance(
            value, (date, pd.Timestamp, np.datetime64)
        ):
            raise YieldForecastError(
                "date values must be valid timezone-free dates"
            )
        try:
            parsed = pd.Timestamp(value)
        except (OverflowError, TypeError, ValueError) as exc:
            raise YieldForecastError(
                "date values must be valid timezone-free dates"
            ) from exc
        if pd.isna(parsed) or parsed.tzinfo is not None or parsed != parsed.normalize():
            raise YieldForecastError(
                "date values must be valid timezone-free dates"
            )
        return parsed

    try:
        return values.map(parse).astype("datetime64[ns]")
    except (OverflowError, TypeError, ValueError) as exc:
        raise YieldForecastError(
            "date values must be valid timezone-free dates"
        ) from exc


def timestamps(values: pd.Series) -> pd.Series:
    def parse(value: object) -> pd.Timestamp:
        if isinstance(value, str):
            if not ISO_TIMESTAMP_PATTERN.fullmatch(value):
                raise YieldForecastError(
                    "timestamp values must be valid timezone-free timestamps"
                )
            try:
                value = datetime.fromisoformat(value)
            except ValueError as exc:
                raise YieldForecastError(
                    "timestamp values must be valid timezone-free timestamps"
                ) from exc
        if not isinstance(value, (datetime, pd.Timestamp, np.datetime64)):
            raise YieldForecastError(
                "timestamp values must be valid timezone-free timestamps"
            )
        try:
            parsed = pd.Timestamp(value)
        except (OverflowError, TypeError, ValueError) as exc:
            raise YieldForecastError(
                "timestamp values must be valid timezone-free timestamps"
            ) from exc
        if pd.isna(parsed) or parsed.tzinfo is not None:
            raise YieldForecastError(
                "timestamp values must be valid timezone-free timestamps"
            )
        return parsed

    try:
        return values.map(parse).astype("datetime64[ns]")
    except (OverflowError, TypeError, ValueError) as exc:
        raise YieldForecastError(
            "timestamp values must be valid timezone-free timestamps"
        ) from exc
