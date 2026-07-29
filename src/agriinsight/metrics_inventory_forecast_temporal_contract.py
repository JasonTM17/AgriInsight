from __future__ import annotations

from datetime import date

import pandas as pd

from agriinsight.inventory_demand_forecast import (
    BACKTEST_STEP_DAYS,
    FORECAST_HORIZON_DAYS,
    FORECAST_LOOKBACK_DAYS,
)
from agriinsight.metrics_inventory_forecast_error import (
    InventoryDemandForecastGoldError,
)


def validate_forecast_temporal_evidence(
    forecast: pd.DataFrame,
    as_of_date: date,
) -> None:
    """Validate inclusive history span and deterministic backtest cadence."""

    for history_start, history_days in forecast[
        ["history_start_date", "history_days"]
    ].itertuples(index=False, name=None):
        parsed_start = strict_iso_date(history_start, "history start")
        expected_days = (as_of_date - parsed_start).days + 1
        if expected_days != int(history_days) or not 1 <= expected_days <= 180:
            raise InventoryDemandForecastGoldError(
                "forecast history date span is invalid"
            )

    for status, history_days, actual_windows in forecast[
        ["forecast_status", "history_days", "backtest_windows"]
    ].itertuples(index=False, name=None):
        if int(actual_windows) != _expected_backtest_windows(
            status,
            int(history_days),
        ):
            raise InventoryDemandForecastGoldError(
                "forecast backtest window count is invalid"
            )


def strict_iso_date(value: object, label: str) -> date:
    if not isinstance(value, str) or len(value) != 10:
        raise InventoryDemandForecastGoldError(
            f"forecast {label} date is invalid"
        )
    try:
        parsed = date.fromisoformat(value)
    except ValueError as exc:
        raise InventoryDemandForecastGoldError(
            f"forecast {label} date is invalid"
        ) from exc
    if parsed.isoformat() != value:
        raise InventoryDemandForecastGoldError(
            f"forecast {label} date is invalid"
        )
    return parsed


def _expected_backtest_windows(status: str, history_days: int) -> int:
    if status == "no_demand":
        return 0
    usable_days = (
        history_days
        - FORECAST_LOOKBACK_DAYS
        - FORECAST_HORIZON_DAYS
    )
    if usable_days < 0:
        return 0
    return usable_days // BACKTEST_STEP_DAYS + 1
