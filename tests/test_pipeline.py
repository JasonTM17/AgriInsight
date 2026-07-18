from __future__ import annotations

import json
import sqlite3
from datetime import date
from pathlib import Path

import pandas as pd
import pytest

from agriinsight.config import GenerationConfig
from agriinsight.pipeline import run_pipeline


@pytest.fixture
def small_config() -> GenerationConfig:
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


def test_pipeline_builds_valid_bronze_to_gold_artifacts(
    tmp_path: Path, small_config: GenerationConfig
) -> None:
    manifest = run_pipeline(tmp_path / "artifacts", small_config)
    root = tmp_path / "artifacts"

    assert manifest["quality_status"] == "passed"
    assert (root / "warehouse" / "agriinsight.db").exists()
    assert (root / "gold" / "executive_summary.csv").exists()
    assert (root / "gold" / "inventory_status.csv").exists()
    assert (root / "gold" / "field_health_status.csv").exists()
    assert manifest["row_counts"]["quarantine"]["activities"] >= 2
    assert manifest["row_counts"]["quarantine"]["harvests"] >= 2
    assert manifest["row_counts"]["quarantine"]["inventory_transactions"] >= 2
    assert manifest["row_counts"]["quarantine"]["sensor_readings"] >= 3

    quality = json.loads(
        (root / "quality" / "data_quality_report.json").read_text(encoding="utf-8")
    )
    assert quality["scores"]["before"]["validity_pct"] < 100
    assert quality["scores"]["before"]["uniqueness_pct"] < 100
    assert quality["scores"]["before"]["completeness_pct"] < 100
    assert quality["scores"]["after"]["validity_pct"] == 100
    assert quality["scores"]["after"]["uniqueness_pct"] == 100
    assert quality["remediation_actions"]["units_converted_to_kg"] > 0

    connection = sqlite3.connect(root / "warehouse" / "agriinsight.db")
    try:
        assert connection.execute("PRAGMA foreign_key_check").fetchall() == []
        activity_rows = connection.execute("SELECT COUNT(*) FROM fact_crop_activity").fetchone()[0]
        harvest_rows = connection.execute("SELECT COUNT(*) FROM fact_harvest").fetchone()[0]
        inventory_rows = connection.execute(
            "SELECT COUNT(*) FROM fact_inventory_transaction"
        ).fetchone()[0]
        sensor_rows = connection.execute("SELECT COUNT(*) FROM fact_sensor_reading").fetchone()[0]
        warehouse_revenue = connection.execute(
            "SELECT SUM(revenue_vnd) FROM fact_harvest"
        ).fetchone()[0]
    finally:
        connection.close()

    assert activity_rows == manifest["row_counts"]["silver"]["activities"]
    assert harvest_rows == manifest["row_counts"]["silver"]["harvests"]
    assert inventory_rows == manifest["row_counts"]["silver"]["inventory_transactions"]
    assert sensor_rows == manifest["row_counts"]["silver"]["sensor_readings"]
    executive = pd.read_csv(root / "gold" / "executive_summary.csv").iloc[0]
    assert executive["total_revenue_vnd"] == pytest.approx(warehouse_revenue)
    assert executive["profit_vnd"] == pytest.approx(
        executive["total_revenue_vnd"] - executive["total_cost_vnd"]
    )

    inventory_status = pd.read_csv(root / "gold" / "inventory_status.csv")
    inventory_summary = pd.read_csv(root / "gold" / "inventory_summary.csv").iloc[0]
    inventory_abc = pd.read_csv(root / "gold" / "inventory_abc.csv")
    assert set(inventory_status["stock_status"]) <= {
        "healthy",
        "low_stock",
        "stockout",
        "overstock",
    }
    assert set(inventory_status["abc_class"]) <= {"A", "B", "C"}
    assert inventory_summary["total_inventory_value_vnd"] == pytest.approx(
        inventory_status["inventory_value_vnd"].sum()
    )
    assert inventory_abc["cumulative_value_share_pct"].is_monotonic_increasing
    assert inventory_abc["cumulative_value_share_pct"].iloc[-1] == pytest.approx(100.0)

    field_health = pd.read_csv(root / "gold" / "field_health_status.csv")
    assert field_health["risk_score"].between(0, 100).all()
    assert set(field_health["risk_status"]) <= {"healthy", "watch", "high"}
    assert (field_health.loc[field_health["risk_status"] == "high", "risk_score"] >= 50).all()
    assert (
        field_health.loc[field_health["risk_status"] == "watch", "risk_score"].between(25, 49)
    ).all()


def test_pipeline_is_reproducible_for_same_seed(
    tmp_path: Path, small_config: GenerationConfig
) -> None:
    first_root = tmp_path / "first"
    second_root = tmp_path / "second"
    first_manifest = run_pipeline(first_root, small_config)
    second_manifest = run_pipeline(second_root, small_config)

    assert first_manifest["run_id"] == second_manifest["run_id"]
    assert first_manifest["row_counts"] == second_manifest["row_counts"]
    for relative_path in (
        Path("bronze/activities.csv"),
        Path("silver/activities.csv"),
        Path("gold/executive_summary.csv"),
        Path("gold/farm_performance.csv"),
        Path("quality/data_quality_report.json"),
    ):
        assert (first_root / relative_path).read_bytes() == (second_root / relative_path).read_bytes()


def test_pipeline_can_be_rerun_in_place(
    tmp_path: Path, small_config: GenerationConfig
) -> None:
    root = tmp_path / "artifacts"
    first = run_pipeline(root, small_config)
    second = run_pipeline(root, small_config)

    assert first["run_id"] == second["run_id"]
    assert first["row_counts"] == second["row_counts"]
    assert not list(root.rglob("*.tmp"))
    assert not list(root.rglob("*.tmp.db"))
