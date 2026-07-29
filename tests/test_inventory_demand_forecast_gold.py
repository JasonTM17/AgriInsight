from __future__ import annotations

import sqlite3
from datetime import date, timedelta

import pandas as pd
import pytest

from agriinsight.inventory_demand_forecast import FORECAST_COLUMNS, MODEL_VERSION
from agriinsight.metrics_inventory_forecast import (
    INVENTORY_STATUS_FORECAST_COLUMNS,
    attach_inventory_demand_forecast,
    build_inventory_demand_forecast_gold,
)


AS_OF = date(2026, 6, 29)


def _forecast_row(
    *,
    warehouse_code: str = "WH-001",
    material_code: str = "MAT-001",
    base_unit: str = "kg",
) -> dict[str, object]:
    return {
        "as_of_date": AS_OF.isoformat(),
        "warehouse_code": warehouse_code,
        "material_code": material_code,
        "base_unit": base_unit,
        "model_version": MODEL_VERSION,
        "forecast_status": "ready",
        "history_start_date": (AS_OF - timedelta(days=179)).isoformat(),
        "history_end_date": AS_OF.isoformat(),
        "history_days": 180,
        "nonzero_demand_days": 180,
        "horizon_days": 30,
        "forecast_quantity": 30.0,
        "lower_quantity": 20.0,
        "upper_quantity": 50.0,
        "backtest_windows": 9,
        "backtest_mae": 1.0,
        "backtest_wape_pct": 5.0,
    }


def _status_frame(*, include_second: bool = False) -> pd.DataFrame:
    rows = [
        {
            "warehouse_code": "WH-001",
            "material_code": "MAT-001",
            "base_unit": "kg",
            "stock_quantity": 10.0,
            "recommended_order_quantity": 99.0,
            "predicted_30d_need": 777.0,
        }
    ]
    if include_second:
        rows.append(
            {
                "warehouse_code": "WH-002",
                "material_code": "MAT-002",
                "base_unit": "liter",
                "stock_quantity": 12.0,
                "recommended_order_quantity": 88.0,
                "predicted_30d_need": 666.0,
            }
        )
    return pd.DataFrame(rows)


def _forecast_frame(**overrides: object) -> pd.DataFrame:
    row = {**_forecast_row(), **overrides}
    return pd.DataFrame([row], columns=FORECAST_COLUMNS)


def _movement_database() -> sqlite3.Connection:
    connection = sqlite3.connect(":memory:")
    connection.executescript(
        """
        CREATE TABLE dim_warehouse (
            warehouse_key INTEGER PRIMARY KEY,
            warehouse_code TEXT NOT NULL
        );
        CREATE TABLE dim_material (
            material_key INTEGER PRIMARY KEY,
            material_code TEXT NOT NULL,
            base_unit TEXT NOT NULL
        );
        CREATE TABLE fact_inventory_transaction (
            transaction_id TEXT PRIMARY KEY,
            warehouse_key INTEGER NOT NULL,
            material_key INTEGER NOT NULL,
            transaction_date TEXT NOT NULL,
            transaction_type TEXT NOT NULL,
            quantity_base_unit,
            base_unit TEXT NOT NULL
        );
        INSERT INTO dim_warehouse VALUES (1, 'WH-002'), (2, 'WH-001');
        INSERT INTO dim_material VALUES
            (1, 'MAT-002', 'liter'),
            (2, 'MAT-001', 'kg');
        """
    )
    start = AS_OF - timedelta(days=179)
    rows = [
        (
            f"TX-{offset:03d}",
            2,
            2,
            (start + timedelta(days=offset)).isoformat(),
            "OUT",
            1.0,
            "kg",
        )
        for offset in range(180)
    ]
    rows.extend(
        [
            ("TX-IN", 1, 1, start.isoformat(), "IN", 10.0, "liter"),
            (
                "TX-OLD",
                2,
                2,
                (start - timedelta(days=1)).isoformat(),
                "BROKEN",
                "not-numeric",
                "kg",
            ),
            (
                "TX-FUTURE",
                2,
                2,
                (AS_OF + timedelta(days=1)).isoformat(),
                "BROKEN",
                "not-numeric",
                "kg",
            ),
        ]
    )
    connection.executemany(
        """
        INSERT INTO fact_inventory_transaction
        VALUES (?, ?, ?, ?, ?, ?, ?)
        """,
        rows,
    )
    connection.commit()
    return connection


def test_gold_builder_is_bounded_sorted_deterministic_and_read_only() -> None:
    connection = _movement_database()
    before = connection.execute(
        "SELECT COUNT(*) FROM fact_inventory_transaction"
    ).fetchone()[0]

    first = build_inventory_demand_forecast_gold(connection, AS_OF)
    second = build_inventory_demand_forecast_gold(connection, AS_OF)
    after = connection.execute(
        "SELECT COUNT(*) FROM fact_inventory_transaction"
    ).fetchone()[0]

    assert list(first.columns) == list(FORECAST_COLUMNS)
    pd.testing.assert_frame_equal(first, second)
    assert first["warehouse_code"].tolist() == ["WH-001", "WH-002"]
    assert first["forecast_status"].tolist() == ["ready", "no_demand"]
    assert first["as_of_date"].eq(AS_OF.isoformat()).all()
    assert before == after


def test_join_preserves_current_policy_and_adds_upper_range_evidence() -> None:
    status = _status_frame()
    forecast = _forecast_frame()
    status_before = status.copy(deep=True)
    forecast_before = forecast.copy(deep=True)

    joined = attach_inventory_demand_forecast(status, forecast, AS_OF)

    assert list(joined.columns[-len(INVENTORY_STATUS_FORECAST_COLUMNS) :]) == list(
        INVENTORY_STATUS_FORECAST_COLUMNS
    )
    result = joined.iloc[0]
    assert result["recommended_order_quantity"] == 99.0
    assert result["predicted_30d_need"] == 777.0
    assert result["forecast_coverage_status"] == "ready"
    assert result["forecast_quantity"] == 30.0
    assert result["forecast_days_of_supply"] == pytest.approx(10.0)
    assert result["forecast_suggested_order_quantity"] == pytest.approx(40.0)
    pd.testing.assert_frame_equal(status, status_before)
    pd.testing.assert_frame_equal(forecast, forecast_before)


def test_missing_forecast_is_explicitly_unavailable_without_order_advice() -> None:
    joined = attach_inventory_demand_forecast(
        _status_frame(include_second=True),
        _forecast_frame(),
        AS_OF,
    )

    missing = joined.loc[joined["warehouse_code"] == "WH-002"].iloc[0]
    assert missing["forecast_coverage_status"] == "unavailable"
    assert pd.isna(missing["forecast_quantity"])
    assert pd.isna(missing["forecast_days_of_supply"])
    assert pd.isna(missing["forecast_suggested_order_quantity"])
