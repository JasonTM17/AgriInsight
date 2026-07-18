from __future__ import annotations

import sqlite3
from datetime import date
from pathlib import Path

import numpy as np
import pandas as pd
import pytest

from agriinsight.config import GenerationConfig
from agriinsight.metrics_cost_analysis import build_cost_analysis_gold
from agriinsight.metrics_cost_contracts import COST_GOLD_CONTRACTS
from agriinsight.pipeline import run_pipeline


GOLD_COST_TABLES = (
    "cost_summary",
    "cost_monthly",
    "cost_farm",
    "cost_season",
    "cost_activity",
    "cost_activity_detail",
    "procurement_summary",
    "procurement_detail",
    "cost_reconciliation",
)


def _small_config() -> GenerationConfig:
    return GenerationConfig(
        seed=42,
        as_of_date=date(2026, 7, 18),
        farm_count=2,
        fields_per_farm=2,
        activities_per_season=6,
        material_count=5,
        sensor_history_days=14,
        sensor_readings_per_day=1,
    )


def _read_gold(root: Path, name: str) -> pd.DataFrame:
    return pd.read_csv(root / "gold" / f"{name}.csv")


def test_cost_gold_contracts_reconcile_without_semantic_double_counting(
    tmp_path: Path,
) -> None:
    root = tmp_path / "artifacts"
    run_pipeline(root, _small_config())

    connection = sqlite3.connect(root / "warehouse" / "agriinsight.db")
    try:
        operating_total = float(
            connection.execute(
                "SELECT COALESCE(SUM(total_cost_vnd), 0) FROM fact_crop_activity"
            ).fetchone()[0]
        )
        inbound_spend = float(
            connection.execute(
                """
                SELECT COALESCE(SUM(total_amount_vnd), 0)
                FROM fact_inventory_transaction
                WHERE transaction_type = 'IN'
                """
            ).fetchone()[0]
        )
        inbound_count = int(
            connection.execute(
                """
                SELECT COUNT(*)
                FROM fact_inventory_transaction
                WHERE transaction_type = 'IN'
                """
            ).fetchone()[0]
        )
        revenue_total = float(
            connection.execute(
                "SELECT COALESCE(SUM(revenue_vnd), 0) FROM fact_harvest"
            ).fetchone()[0]
        )
        budget_total = float(
            connection.execute(
                "SELECT COALESCE(SUM(budget_cost_vnd), 0) FROM dim_season"
            ).fetchone()[0]
        )
    finally:
        connection.close()

    frames = {name: _read_gold(root, name) for name in GOLD_COST_TABLES}
    assert set(frames) == set(COST_GOLD_CONTRACTS)
    for name, frame in frames.items():
        assert tuple(frame.columns) == COST_GOLD_CONTRACTS[name].columns
        assert not frame.empty, name
        numeric = frame.select_dtypes(include="number")
        assert np.isfinite(numeric.to_numpy()).all(), name

    summary = frames["cost_summary"].iloc[0]
    executive = _read_gold(root, "executive_summary").iloc[0]
    assert summary["operating_total_cost_vnd"] == pytest.approx(operating_total)
    assert summary["operating_total_cost_vnd"] == pytest.approx(
        executive["total_cost_vnd"]
    )
    assert summary["revenue_vnd"] == pytest.approx(revenue_total)
    assert summary["budget_operating_cost_vnd"] == pytest.approx(budget_total)

    for name in (
        "cost_monthly",
        "cost_farm",
        "cost_season",
        "cost_activity",
        "cost_activity_detail",
    ):
        assert frames[name]["operating_total_cost_vnd"].sum() == pytest.approx(
            operating_total
        )

    for name in ("cost_monthly", "cost_farm", "cost_season"):
        assert frames[name]["revenue_vnd"].sum() == pytest.approx(revenue_total)
    for name in ("cost_farm", "cost_season"):
        assert frames[name]["budget_operating_cost_vnd"].sum() == pytest.approx(
            budget_total
        )

    detail = frames["cost_activity_detail"]
    assert detail["activity_id"].is_unique
    assert detail["operating_total_cost_vnd"].to_numpy() == pytest.approx(
        (
            detail["operating_material_cost_vnd"]
            + detail["operating_labor_cost_vnd"]
        ).to_numpy()
    )

    procurement_detail = frames["procurement_detail"]
    procurement_summary = frames["procurement_summary"]
    assert len(procurement_detail) == inbound_count
    assert set(procurement_detail["transaction_type"]) == {"IN"}
    assert procurement_detail["transaction_id"].is_unique
    assert procurement_detail["procurement_spend_vnd"].sum() == pytest.approx(
        inbound_spend
    )
    assert procurement_summary["procurement_spend_vnd"].sum() == pytest.approx(
        inbound_spend
    )

    for name in (
        "cost_summary",
        "cost_monthly",
        "cost_farm",
        "cost_season",
        "cost_activity",
        "cost_activity_detail",
        "cost_reconciliation",
    ):
        assert not any("procurement" in column for column in frames[name].columns)
        assert "inventory_value_vnd" not in frames[name].columns

    reconciliation = frames["cost_reconciliation"]
    assert reconciliation["component_delta_vnd"].abs().max() == pytest.approx(0.0)
    assert set(reconciliation["reconciliation_status"]) == {"balanced"}


def test_cost_gold_contracts_are_deterministically_ordered(tmp_path: Path) -> None:
    first_root = tmp_path / "first"
    second_root = tmp_path / "second"
    run_pipeline(first_root, _small_config())
    run_pipeline(second_root, _small_config())

    for name in GOLD_COST_TABLES:
        assert (first_root / "gold" / f"{name}.csv").read_bytes() == (
            second_root / "gold" / f"{name}.csv"
        ).read_bytes()


def test_cost_farm_keeps_farms_without_seasons(tmp_path: Path) -> None:
    root = tmp_path / "artifacts"
    run_pipeline(root, _small_config())

    connection = sqlite3.connect(root / "warehouse" / "agriinsight.db")
    try:
        connection.execute(
            """
            INSERT INTO dim_farm (
                farm_key,
                farm_code,
                farm_name,
                province,
                registered_area_ha,
                latitude,
                longitude
            ) VALUES (9999, 'FARM-NO-SEASON', 'Farm Without Season', 'Test', 1, 0, 0)
            """
        )
        farm_cost = build_cost_analysis_gold(connection)["cost_farm"]
    finally:
        connection.rollback()
        connection.close()

    row = farm_cost.loc[farm_cost["farm_code"] == "FARM-NO-SEASON"].iloc[0]
    assert row["season_count"] == 0
    assert row["operating_total_cost_vnd"] == pytest.approx(0.0)
    assert row["budget_operating_cost_vnd"] == pytest.approx(0.0)
    assert row["operating_cost_per_ha_vnd"] == pytest.approx(0.0)
