from __future__ import annotations

import os
import sqlite3
from importlib import resources
from pathlib import Path
from typing import Any

import pandas as pd

from agriinsight.config import GenerationConfig


def _surrogate_dimension(
    frame: pd.DataFrame, key_name: str, sort_column: str
) -> pd.DataFrame:
    dimension = frame.sort_values(sort_column).reset_index(drop=True).copy()
    dimension.insert(0, key_name, range(1, len(dimension) + 1))
    return dimension


def _build_dimensions(tables: dict[str, pd.DataFrame], as_of_date: str) -> dict[str, pd.DataFrame]:
    farms = _surrogate_dimension(tables["farms"], "farm_key", "farm_code")

    fields = tables["fields"].merge(
        farms[["farm_key", "farm_code"]], on="farm_code", how="inner", validate="many_to_one"
    )
    fields = fields.drop(columns=["farm_code"])
    fields = _surrogate_dimension(fields, "field_key", "field_code")

    crops = _surrogate_dimension(tables["crops"], "crop_key", "crop_code")

    seasons = (
        tables["seasons"]
        .merge(
            fields[["field_key", "field_code", "farm_key"]],
            on="field_code",
            how="inner",
            validate="many_to_one",
        )
        .merge(
            crops[["crop_key", "crop_code"]],
            on="crop_code",
            how="inner",
            validate="many_to_one",
        )
        .drop(columns=["field_code", "crop_code"])
    )
    seasons = _surrogate_dimension(seasons, "season_key", "season_code")

    activity_types = pd.DataFrame(
        {"activity_type": sorted(tables["activities"]["activity_type"].dropna().unique())}
    )
    activity_types = _surrogate_dimension(
        activity_types, "activity_type_key", "activity_type"
    )

    warehouses = tables["warehouses"].merge(
        farms[["farm_key", "farm_code"]], on="farm_code", how="inner", validate="many_to_one"
    )
    warehouses = warehouses.drop(columns=["farm_code"])
    warehouses["temperature_controlled"] = warehouses["temperature_controlled"].astype(int)
    warehouses = _surrogate_dimension(warehouses, "warehouse_key", "warehouse_code")

    materials = _surrogate_dimension(tables["materials"], "material_key", "material_code")
    suppliers = _surrogate_dimension(tables["suppliers"], "supplier_key", "supplier_code")

    sensors = tables["sensors"].merge(
        fields[["field_key", "field_code", "farm_key"]],
        on="field_code",
        how="inner",
        validate="many_to_one",
    )
    sensors = sensors.drop(columns=["field_code"])
    sensors = _surrogate_dimension(sensors, "sensor_key", "sensor_code")
    pest_types = _surrogate_dimension(tables["pest_types"], "pest_key", "pest_code")

    date_candidates = pd.concat(
        [
            pd.to_datetime(tables["seasons"]["start_date"], errors="raise"),
            pd.to_datetime(tables["seasons"]["expected_harvest_date"], errors="raise"),
            pd.to_datetime(tables["activities"]["occurred_at"], errors="raise"),
            pd.to_datetime(tables["harvests"]["harvested_at"], errors="raise"),
            pd.to_datetime(tables["inventory_transactions"]["transaction_date"], errors="raise"),
            pd.to_datetime(tables["sensor_readings"]["observed_at"], errors="raise"),
            pd.to_datetime(tables["weather_daily"]["weather_date"], errors="raise"),
            pd.to_datetime(tables["crop_health_observations"]["observed_at"], errors="raise"),
            pd.Series([pd.Timestamp(as_of_date)]),
        ],
        ignore_index=True,
    )
    calendar = pd.date_range(date_candidates.min().normalize(), date_candidates.max().normalize())
    iso_calendar = calendar.isocalendar()
    dates = pd.DataFrame(
        {
            "date_key": calendar.strftime("%Y%m%d").astype(int),
            "full_date": calendar.strftime("%Y-%m-%d"),
            "year": calendar.year,
            "quarter": calendar.quarter,
            "month": calendar.month,
            "month_name": calendar.strftime("%B"),
            "week": iso_calendar.week.to_numpy(dtype=int),
            "day": calendar.day,
        }
    )

    return {
        "dim_date": dates,
        "dim_farm": farms,
        "dim_field": fields,
        "dim_crop": crops,
        "dim_season": seasons,
        "dim_activity_type": activity_types,
        "dim_warehouse": warehouses,
        "dim_material": materials,
        "dim_supplier": suppliers,
        "dim_sensor": sensors,
        "dim_pest_type": pest_types,
    }


def _build_facts(
    tables: dict[str, pd.DataFrame], dimensions: dict[str, pd.DataFrame]
) -> dict[str, pd.DataFrame]:
    season_lookup = dimensions["dim_season"][
        ["season_key", "season_code", "field_key", "farm_key", "crop_key"]
    ]
    activity_type_lookup = dimensions["dim_activity_type"]

    activities = (
        tables["activities"]
        .merge(season_lookup, on="season_code", how="inner", validate="many_to_one")
        .merge(
            activity_type_lookup,
            on="activity_type",
            how="inner",
            validate="many_to_one",
        )
    )
    activity_dates = pd.to_datetime(activities["occurred_at"], errors="raise")
    activities["date_key"] = activity_dates.dt.strftime("%Y%m%d").astype(int)
    activities = activities[
        [
            "activity_id",
            "date_key",
            "farm_key",
            "field_key",
            "crop_key",
            "season_key",
            "activity_type_key",
            "occurred_at",
            "material_name",
            "quantity_kg",
            "labor_hours",
            "material_cost_vnd",
            "labor_cost_vnd",
            "total_cost_vnd",
            "notes",
        ]
    ]

    harvests = tables["harvests"].merge(
        season_lookup, on="season_code", how="inner", validate="many_to_one"
    )
    harvest_dates = pd.to_datetime(harvests["harvested_at"], errors="raise")
    harvests["date_key"] = harvest_dates.dt.strftime("%Y%m%d").astype(int)
    harvests = harvests[
        [
            "harvest_id",
            "date_key",
            "farm_key",
            "field_key",
            "crop_key",
            "season_key",
            "harvested_at",
            "harvest_quantity_kg",
            "waste_quantity_kg",
            "quality_grade",
            "revenue_vnd",
        ]
    ]

    warehouse_lookup = dimensions["dim_warehouse"][
        ["warehouse_key", "warehouse_code", "farm_key"]
    ]
    material_lookup = dimensions["dim_material"][["material_key", "material_code"]]
    supplier_lookup = dimensions["dim_supplier"][["supplier_key", "supplier_code"]]
    inventory = (
        tables["inventory_transactions"]
        .merge(warehouse_lookup, on="warehouse_code", how="inner", validate="many_to_one")
        .merge(material_lookup, on="material_code", how="inner", validate="many_to_one")
        .merge(supplier_lookup, on="supplier_code", how="left", validate="many_to_one")
    )
    inventory["date_key"] = pd.to_datetime(
        inventory["transaction_date"], errors="raise"
    ).dt.strftime("%Y%m%d").astype(int)
    inventory["supplier_key"] = inventory["supplier_key"].where(
        inventory["supplier_key"].notna(), None
    )
    inventory = inventory[
        [
            "transaction_id",
            "date_key",
            "warehouse_key",
            "farm_key",
            "material_key",
            "supplier_key",
            "transaction_date",
            "transaction_type",
            "quantity_base_unit",
            "base_unit",
            "unit_cost_base_unit_vnd",
            "total_amount_vnd",
            "batch_code",
            "expiry_date",
        ]
    ]

    sensor_lookup = dimensions["dim_sensor"][
        ["sensor_key", "sensor_code", "field_key", "farm_key"]
    ]
    readings = tables["sensor_readings"].merge(
        sensor_lookup, on="sensor_code", how="inner", validate="many_to_one"
    )
    readings["date_key"] = pd.to_datetime(
        readings["observed_at"], errors="raise"
    ).dt.strftime("%Y%m%d").astype(int)
    readings = readings[
        [
            "reading_id",
            "date_key",
            "sensor_key",
            "farm_key",
            "field_key",
            "observed_at",
            "temperature_c",
            "air_humidity_pct",
            "soil_moisture_pct",
            "soil_ph",
            "rainfall_mm",
            "battery_pct",
        ]
    ]

    farm_lookup = dimensions["dim_farm"][["farm_key", "farm_code"]]
    weather = tables["weather_daily"].merge(
        farm_lookup, on="farm_code", how="inner", validate="many_to_one"
    )
    weather["date_key"] = pd.to_datetime(
        weather["weather_date"], errors="raise"
    ).dt.strftime("%Y%m%d").astype(int)
    weather = weather[
        [
            "weather_id",
            "date_key",
            "farm_key",
            "weather_date",
            "temperature_avg_c",
            "humidity_avg_pct",
            "rainfall_mm",
            "wind_speed_kph",
            "source",
        ]
    ]

    pest_lookup = dimensions["dim_pest_type"][["pest_key", "pest_code"]]
    health = (
        tables["crop_health_observations"]
        .merge(season_lookup, on="season_code", how="inner", validate="many_to_one")
        .merge(pest_lookup, on="pest_code", how="inner", validate="many_to_one")
    )
    health["date_key"] = pd.to_datetime(
        health["observed_at"], errors="raise"
    ).dt.strftime("%Y%m%d").astype(int)
    health = health[
        [
            "observation_id",
            "date_key",
            "farm_key",
            "field_key",
            "crop_key",
            "season_key",
            "pest_key",
            "observed_at",
            "severity",
            "affected_area_pct",
            "plant_mortality_pct",
            "notes",
        ]
    ]

    return {
        "fact_crop_activity": activities,
        "fact_harvest": harvests,
        "fact_inventory_transaction": inventory,
        "fact_sensor_reading": readings,
        "fact_weather_daily": weather,
        "fact_crop_health_observation": health,
    }


def build_warehouse(
    tables: dict[str, pd.DataFrame], db_path: Path, config: GenerationConfig
) -> dict[str, Any]:
    """Build an atomic SQLite star-schema warehouse and validate all foreign keys."""

    db_path.parent.mkdir(parents=True, exist_ok=True)
    temp_path = db_path.with_suffix(".tmp.db")
    if temp_path.exists():
        temp_path.unlink()

    schema = resources.files("agriinsight").joinpath("sqlite_schema.sql").read_text(encoding="utf-8")
    dimensions = _build_dimensions(tables, config.as_of_date.isoformat())
    facts = _build_facts(tables, dimensions)

    connection = sqlite3.connect(temp_path)
    try:
        connection.execute("PRAGMA foreign_keys = ON")
        connection.executescript(schema)
        for table_name, frame in dimensions.items():
            frame.to_sql(table_name, connection, if_exists="append", index=False)
        for table_name, frame in facts.items():
            frame.to_sql(table_name, connection, if_exists="append", index=False)
        connection.execute(
            "INSERT INTO etl_run(run_id, seed, as_of_date, source_name) VALUES (?, ?, ?, ?)",
            (
                f"synthetic-{config.as_of_date.isoformat()}-{config.seed}",
                config.seed,
                config.as_of_date.isoformat(),
                "agriinsight.synthetic.v1",
            ),
        )
        foreign_key_violations = connection.execute("PRAGMA foreign_key_check").fetchall()
        if foreign_key_violations:
            raise RuntimeError(f"warehouse foreign-key violations: {foreign_key_violations[:5]}")
        connection.commit()
    except Exception:
        connection.rollback()
        raise
    finally:
        connection.close()

    os.replace(temp_path, db_path)
    row_counts = {
        table_name: len(frame) for table_name, frame in {**dimensions, **facts}.items()
    }
    return {
        "database": str(db_path),
        "foreign_key_violations": 0,
        "row_counts": row_counts,
    }
