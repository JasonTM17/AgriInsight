from __future__ import annotations

from datetime import date, timedelta

import numpy as np
import pandas as pd
import pytest

from agriinsight.inventory_demand_forecast import (
    FORECAST_COLUMNS,
    MODEL_VERSION,
    InventoryDemandForecastError,
    forecast_inventory_demand,
)


INPUT_COLUMNS = (
    "transaction_date",
    "warehouse_code",
    "material_code",
    "base_unit",
    "transaction_type",
    "quantity_base_unit",
)


def _movement(
    transaction_date: date,
    quantity: object,
    *,
    warehouse_code: str = "WH-001",
    material_code: str = "MAT-NPK",
    base_unit: str = "kg",
    transaction_type: str = "OUT",
) -> dict[str, object]:
    return {
        "transaction_date": transaction_date.isoformat(),
        "warehouse_code": warehouse_code,
        "material_code": material_code,
        "base_unit": base_unit,
        "transaction_type": transaction_type,
        "quantity_base_unit": quantity,
    }


def _frame(rows: list[dict[str, object]]) -> pd.DataFrame:
    return pd.DataFrame(rows, columns=INPUT_COLUMNS)


def test_ready_forecast_is_backtested_bounded_and_ignores_future_facts() -> None:
    as_of = date(2026, 6, 29)
    start = as_of - timedelta(days=179)
    rows = [
        _movement(start + timedelta(days=offset), float(offset % 7 + 1))
        for offset in range(180)
    ]
    expected_point = sum(float(offset % 7 + 1) for offset in range(90, 180)) / 90 * 30

    baseline = forecast_inventory_demand(_frame(rows), as_of)
    with_outside_window = forecast_inventory_demand(
        _frame(
            [
                *rows,
                _movement(start - timedelta(days=1), 1_000_000.0),
                _movement(as_of + timedelta(days=1), 1_000_000.0),
            ]
        ),
        as_of,
    )

    assert list(baseline.columns) == list(FORECAST_COLUMNS)
    assert baseline.to_dict("records") == with_outside_window.to_dict("records")
    result = baseline.iloc[0]
    assert result["model_version"] == MODEL_VERSION
    assert result["forecast_status"] == "ready"
    assert result["history_days"] == 180
    assert result["nonzero_demand_days"] == 180
    assert result["horizon_days"] == 30
    assert result["forecast_quantity"] == pytest.approx(expected_point)
    assert 0 <= result["lower_quantity"] <= result["forecast_quantity"]
    assert result["forecast_quantity"] <= result["upper_quantity"]
    assert result["backtest_windows"] >= 2
    assert np.isfinite(result["backtest_mae"])
    assert np.isfinite(result["backtest_wape_pct"])


def test_empirical_range_and_weekly_backtest_match_known_step_change() -> None:
    as_of = date(2026, 6, 29)
    start = as_of - timedelta(days=179)
    rows = [
        _movement(start + timedelta(days=offset), 1.0 if offset < 90 else 10.0)
        for offset in range(180)
    ]

    result = forecast_inventory_demand(_frame(rows), as_of).iloc[0]
    changed_tail = [
        {**row, "quantity_base_unit": 1_000.0} if offset >= 176 else row
        for offset, row in enumerate(rows)
    ]
    changed_result = forecast_inventory_demand(_frame(changed_tail), as_of).iloc[0]

    assert result["forecast_quantity"] == pytest.approx(300.0)
    assert result["lower_quantity"] == pytest.approx(30.0)
    assert result["upper_quantity"] == pytest.approx(300.0)
    assert result["backtest_windows"] == 9
    assert result["backtest_mae"] == pytest.approx(186.0)
    assert result["backtest_wape_pct"] == pytest.approx(62.0)
    assert changed_result["forecast_quantity"] != result["forecast_quantity"]
    assert changed_result["backtest_windows"] == result["backtest_windows"]
    assert changed_result["backtest_mae"] == result["backtest_mae"]
    assert changed_result["backtest_wape_pct"] == result["backtest_wape_pct"]


def test_sparse_demand_uses_dense_zero_days_instead_of_nonzero_mean() -> None:
    as_of = date(2026, 6, 29)
    start = as_of - timedelta(days=179)
    rows = [_movement(start + timedelta(days=offset), 30.0) for offset in range(0, 180, 30)]

    result = forecast_inventory_demand(_frame(rows), as_of).iloc[0]

    assert result["forecast_status"] == "ready"
    assert result["nonzero_demand_days"] == 6
    assert result["forecast_quantity"] == pytest.approx(30.0)
    assert result["forecast_quantity"] != pytest.approx(900.0)


def test_no_demand_returns_zero_without_fabricated_accuracy() -> None:
    as_of = date(2026, 6, 29)
    start = as_of - timedelta(days=179)
    rows = [
        _movement(start, 100.0, transaction_type="IN"),
        _movement(as_of, 50.0, transaction_type="IN"),
    ]

    result = forecast_inventory_demand(_frame(rows), as_of).iloc[0]

    assert result["forecast_status"] == "no_demand"
    assert result["forecast_quantity"] == 0
    assert result["lower_quantity"] == 0
    assert result["upper_quantity"] == 0
    assert result["backtest_windows"] == 0
    assert pd.isna(result["backtest_mae"])
    assert pd.isna(result["backtest_wape_pct"])


def test_short_history_is_explicit_and_has_no_accuracy_claim() -> None:
    as_of = date(2026, 6, 29)
    start = as_of - timedelta(days=39)
    rows = [_movement(start + timedelta(days=offset), 1.0) for offset in range(40)]

    result = forecast_inventory_demand(_frame(rows), as_of).iloc[0]

    assert result["forecast_status"] == "insufficient_history"
    assert result["history_days"] == 40
    assert result["forecast_quantity"] == pytest.approx(30.0)
    assert result["backtest_windows"] == 0
    assert pd.isna(result["backtest_mae"])
    assert pd.isna(result["backtest_wape_pct"])


def test_output_is_stable_sorted_and_empty_input_keeps_schema() -> None:
    as_of = date(2026, 6, 29)
    start = as_of - timedelta(days=179)
    rows = [
        _movement(start + timedelta(days=offset), 1.0, warehouse_code=warehouse)
        for warehouse in ("WH-002", "WH-001")
        for offset in range(180)
    ]
    shuffled = _frame(rows).sample(frac=1, random_state=7).reset_index(drop=True)

    first = forecast_inventory_demand(shuffled, as_of)
    second = forecast_inventory_demand(shuffled, as_of)
    empty = forecast_inventory_demand(_frame([]), as_of)

    assert first.equals(second)
    assert first["warehouse_code"].tolist() == ["WH-001", "WH-002"]
    assert empty.empty
    assert list(empty.columns) == list(FORECAST_COLUMNS)


def test_same_day_movement_order_cannot_change_forecast_bytes() -> None:
    as_of = date(2026, 6, 29)
    start = as_of - timedelta(days=179)
    anchor = _movement(start, 0.0)
    same_day = [
        _movement(as_of, quantity)
        for quantity in (10_000_000_000_000_000.0, 1.0, 1.0)
    ]

    large_first = forecast_inventory_demand(_frame([anchor, *same_day]), as_of)
    small_first = forecast_inventory_demand(
        _frame([anchor, *reversed(same_day)]),
        as_of,
    )

    assert large_first.to_dict("records") == small_first.to_dict("records")


def test_aggregate_overflow_fails_before_emitting_nonfinite_forecast() -> None:
    as_of = date(2026, 6, 29)
    start = as_of - timedelta(days=179)
    rows = [
        _movement(start + timedelta(days=offset), 1e308)
        for offset in range(180)
    ]

    with pytest.raises(InventoryDemandForecastError, match="finite numeric range"):
        forecast_inventory_demand(_frame(rows), as_of)
