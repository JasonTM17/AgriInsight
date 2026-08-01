from __future__ import annotations

from dataclasses import dataclass

import pandas as pd

from agriinsight.yield_forecast_numeric import (
    finite_mean,
    finite_median,
    finite_wape_percent,
)


@dataclass(frozen=True)
class BacktestResult:
    origins: int
    seasons: int
    mae: float | None
    wape_pct: float | None


def backtest_yield_labels(
    all_labels: pd.DataFrame,
    crop_code: str,
    eligible_labels: pd.DataFrame,
    *,
    minimum_training_seasons: int,
) -> BacktestResult:
    errors: list[float] = []
    actuals: list[float] = []
    origins = 0
    crop_labels = all_labels[all_labels["crop_code"] == crop_code]
    for origin, evaluation in eligible_labels.groupby("season_start_at", sort=True):
        training = crop_labels[crop_labels["season_completed_at"] < origin]
        if len(training) < minimum_training_seasons:
            continue
        prediction = finite_median(
            training["yield_kg_per_ha"],
            "backtest prediction",
        )
        origins += 1
        for actual in evaluation["yield_kg_per_ha"]:
            actual_value = float(actual)
            errors.append(abs(prediction - actual_value))
            actuals.append(actual_value)
    if not errors:
        return BacktestResult(origins, 0, None, None)
    return BacktestResult(
        origins,
        len(errors),
        finite_mean(errors, "backtest MAE"),
        finite_wape_percent(errors, actuals),
    )
