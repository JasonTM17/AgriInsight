from __future__ import annotations

import hashlib
import json
import sqlite3
from datetime import date
from pathlib import Path

import pandas as pd
import pytest

from agriinsight.config import GenerationConfig
from agriinsight.metrics import build_gold_datasets
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
    assert manifest["configuration"]["scale_profile"] == "standard"
    assert manifest["configuration"]["nominal_sensor_readings"] == 56
    assert (root / "warehouse" / "agriinsight.db").exists()
    assert (root / "gold" / "executive_summary.csv").exists()
    assert (root / "gold" / "cost_summary.csv").exists()
    assert (root / "gold" / "procurement_detail.csv").exists()
    assert (root / "gold" / "inventory_status.csv").exists()
    forecast_path = root / "gold" / "inventory_demand_forecast.csv"
    assert forecast_path.exists()
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

    seasons = pd.read_csv(root / "silver" / "seasons.csv")
    assert {"season_area_ha", "completed_at"}.issubset(seasons.columns)
    season_years = set(
        pd.to_datetime(seasons["start_date"], errors="raise").dt.year.tolist()
    )
    assert {2024, 2025, 2026}.issubset(season_years)
    completed = seasons["status"].astype("string").str.lower().eq("completed")
    active = seasons["status"].astype("string").str.lower().eq("active")
    assert completed.any()
    assert active.any()
    assert seasons.loc[completed, "season_area_ha"].gt(0).all()
    assert seasons.loc[completed, "completed_at"].notna().all()
    assert seasons.loc[active, "completed_at"].isna().all()

    connection = sqlite3.connect(root / "warehouse" / "agriinsight.db")
    try:
        assert connection.execute("PRAGMA foreign_key_check").fetchall() == []
        valid_season_snapshots = connection.execute(
            """
            SELECT COUNT(*)
            FROM dim_season
            WHERE season_area_ha > 0
              AND (
                  (status = 'completed' AND completed_at IS NOT NULL)
                  OR (status = 'active' AND completed_at IS NULL)
              )
            """
        ).fetchone()[0]
        cohort_2024 = connection.execute(
            "SELECT COUNT(*) FROM dim_season WHERE substr(start_date, 1, 4) = '2024'"
        ).fetchone()[0]
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
    assert valid_season_snapshots == manifest["row_counts"]["silver"]["seasons"]
    assert cohort_2024 > 0
    executive = pd.read_csv(root / "gold" / "executive_summary.csv").iloc[0]
    assert executive["total_revenue_vnd"] == pytest.approx(warehouse_revenue)
    assert executive["profit_vnd"] == pytest.approx(
        executive["total_revenue_vnd"] - executive["total_cost_vnd"]
    )

    inventory_status = pd.read_csv(root / "gold" / "inventory_status.csv")
    inventory_forecast = pd.read_csv(forecast_path)
    inventory_summary = pd.read_csv(root / "gold" / "inventory_summary.csv").iloc[0]
    inventory_abc = pd.read_csv(root / "gold" / "inventory_abc.csv")
    assert set(inventory_status["stock_status"]) <= {
        "healthy",
        "low_stock",
        "stockout",
        "overstock",
    }
    assert set(inventory_status["abc_class"]) <= {"A", "B", "C"}
    assert set(inventory_status["forecast_coverage_status"]) <= {
        "ready",
        "no_demand",
        "insufficient_history",
        "unavailable",
    }
    assert not inventory_forecast.duplicated(
        ["warehouse_code", "material_code"]
    ).any()
    assert inventory_forecast["as_of_date"].eq(
        small_config.as_of_date.isoformat()
    ).all()
    assert inventory_forecast[
        ["warehouse_code", "material_code"]
    ].to_records(index=False).tolist() == sorted(
        inventory_forecast[
            ["warehouse_code", "material_code"]
        ].to_records(index=False).tolist()
    )
    assert (
        manifest["row_counts"]["gold"]["inventory_demand_forecast"]
        == len(inventory_forecast)
    )
    assert manifest["checksums"][
        "gold/inventory_demand_forecast.csv"
    ] == hashlib.sha256(forecast_path.read_bytes()).hexdigest()
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


def test_farm_performance_uses_immutable_season_area_snapshot(
    tmp_path: Path, small_config: GenerationConfig
) -> None:
    root = tmp_path / "artifacts"
    run_pipeline(root, small_config)
    db_path = root / "warehouse" / "agriinsight.db"

    connection = sqlite3.connect(db_path)
    try:
        farm_code, field_key, season_area = connection.execute(
            """
            SELECT f.farm_code, s.field_key, s.season_area_ha
            FROM dim_season s
            JOIN dim_farm f USING (farm_key)
            JOIN fact_harvest h USING (season_key)
            ORDER BY s.season_code
            LIMIT 1
            """
        ).fetchone()
        expected_harvested_area = float(
            connection.execute(
                """
                SELECT COALESCE(SUM(s.season_area_ha), 0)
                FROM (
                    SELECT DISTINCT season_key, farm_key
                    FROM fact_harvest
                    WHERE farm_key = (SELECT farm_key FROM dim_farm WHERE farm_code = ?)
                ) harvested
                JOIN dim_season s USING (season_key)
                """,
                (farm_code,),
            ).fetchone()[0]
        )
        expected_operated_area = float(
            connection.execute(
                """
                SELECT COALESCE(SUM(season_area_ha), 0)
                FROM dim_season
                WHERE farm_key = (SELECT farm_key FROM dim_farm WHERE farm_code = ?)
                """,
                (farm_code,),
            ).fetchone()[0]
        )
        connection.execute(
            "UPDATE dim_field SET area_ha = ? WHERE field_key = ?",
            (float(season_area) * 9, field_key),
        )
        connection.commit()
    finally:
        connection.close()

    farm_metrics = build_gold_datasets(db_path)["farm_performance"]
    row = farm_metrics.loc[farm_metrics["farm_code"] == farm_code].iloc[0]

    assert row["harvested_area_ha"] == pytest.approx(expected_harvested_area)
    assert row["yield_kg_per_ha"] == pytest.approx(
        row["harvest_quantity_kg"] / expected_harvested_area
    )
    assert row["cost_vnd_per_ha"] == pytest.approx(
        row["total_cost_vnd"] / expected_operated_area
    )


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
        Path("gold/cost_summary.csv"),
        Path("gold/procurement_detail.csv"),
        Path("gold/inventory_demand_forecast.csv"),
        Path("gold/inventory_status.csv"),
        Path("quality/data_quality_report.json"),
    ):
        assert (first_root / relative_path).read_bytes() == (second_root / relative_path).read_bytes()


def test_pipeline_can_be_rerun_in_place(
    tmp_path: Path, small_config: GenerationConfig
) -> None:
    root = tmp_path / "artifacts"
    first = run_pipeline(root, small_config)
    stale_gold = root / "gold" / "stale_contract.csv"
    stale_gold.write_text("stale\ncontract\n", encoding="utf-8")
    report_temp = root / "_tmp" / "report-exports" / "in-progress.json"
    report_temp.parent.mkdir(parents=True)
    report_temp.write_text('{"status":"temporary"}', encoding="utf-8")
    second = run_pipeline(root, small_config)

    assert first["run_id"] == second["run_id"]
    assert first["row_counts"] == second["row_counts"]
    assert not stale_gold.exists()
    assert "gold/stale_contract.csv" not in second["checksums"]
    assert report_temp.exists()
    assert not any(path.startswith("_tmp/") for path in second["checksums"])
    assert not list(root.rglob("*.tmp"))
    assert not list(root.rglob("*.tmp.db"))
