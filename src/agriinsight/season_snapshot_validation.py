from __future__ import annotations

from datetime import datetime
import re

import numpy as np
import pandas as pd


_TIMESTAMP_PATTERN = re.compile(
    r"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,6})?$"
)


def timezone_free_timestamp_mask(values: pd.Series) -> pd.Series:
    """Return true only for explicit, timezone-free completion timestamps."""

    def is_valid(value: object) -> bool:
        if value is None or value is pd.NA or value is pd.NaT:
            return False
        if isinstance(value, str):
            return _TIMESTAMP_PATTERN.fullmatch(value) is not None
        if isinstance(value, (datetime, pd.Timestamp)):
            return pd.Timestamp(value).tzinfo is None
        if isinstance(value, np.datetime64):
            return not np.isnat(value)
        return False

    return values.map(is_valid).astype(bool)


def format_timezone_free_timestamps(values: pd.Series) -> pd.Series:
    """Serialize valid timestamps without silently discarding fractional seconds."""

    parsed = pd.to_datetime(values, errors="coerce")
    formatted = parsed.dt.strftime("%Y-%m-%dT%H:%M:%S.%f")
    return formatted.str.rstrip("0").str.rstrip(".")
