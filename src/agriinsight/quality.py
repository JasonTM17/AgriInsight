from __future__ import annotations

from datetime import date
from typing import Any

import pandas as pd


REQUIRED_COLUMNS = {
    "farms": ("farm_code", "farm_name", "registered_area_ha"),
    "fields": ("field_code", "farm_code", "area_ha"),
    "crops": ("crop_code", "crop_name", "category"),
    "seasons": ("season_code", "field_code", "crop_code", "start_date"),
    "activities": ("activity_id", "occurred_at", "field_code", "season_code"),
    "harvests": ("harvest_id", "harvested_at", "field_code", "season_code"),
    "warehouses": ("warehouse_code", "farm_code", "warehouse_name"),
    "materials": ("material_code", "material_name", "category", "base_unit"),
    "suppliers": ("supplier_code", "supplier_name"),
    "inventory_transactions": (
        "transaction_id",
        "transaction_date",
        "warehouse_code",
        "material_code",
        "transaction_type",
    ),
    "sensors": ("sensor_code", "field_code", "status"),
    "sensor_readings": (
        "reading_id",
        "observed_at",
        "sensor_code",
        "field_code",
        "soil_moisture_pct",
    ),
    "weather_daily": ("weather_id", "weather_date", "farm_code"),
    "pest_types": ("pest_code", "pest_name"),
    "crop_health_observations": (
        "observation_id",
        "observed_at",
        "field_code",
        "season_code",
        "pest_code",
    ),
}

PRIMARY_KEYS = {
    "farms": "farm_code",
    "fields": "field_code",
    "crops": "crop_code",
    "seasons": "season_code",
    "activities": "activity_id",
    "harvests": "harvest_id",
    "warehouses": "warehouse_code",
    "materials": "material_code",
    "suppliers": "supplier_code",
    "inventory_transactions": "transaction_id",
    "sensors": "sensor_code",
    "sensor_readings": "reading_id",
    "weather_daily": "weather_id",
    "pest_types": "pest_code",
    "crop_health_observations": "observation_id",
}

FRESHNESS_COLUMNS = (
    ("activities", "occurred_at"),
    ("harvests", "harvested_at"),
    ("inventory_transactions", "transaction_date"),
    ("sensor_readings", "observed_at"),
    ("weather_daily", "weather_date"),
    ("crop_health_observations", "observed_at"),
)


def _completeness(tables: dict[str, pd.DataFrame]) -> float:
    required_cells = 0
    missing_cells = 0
    for name, columns in REQUIRED_COLUMNS.items():
        frame = tables[name]
        required_cells += len(frame) * len(columns)
        missing_cells += int(frame[list(columns)].isna().sum().sum())
    if required_cells == 0:
        return 100.0
    return round(100 * (1 - missing_cells / required_cells), 4)


def _uniqueness(tables: dict[str, pd.DataFrame]) -> tuple[float, int, int]:
    total = 0
    duplicates = 0
    for name, key in PRIMARY_KEYS.items():
        frame = tables[name]
        total += len(frame)
        duplicates += int(frame.duplicated(key, keep="first").sum())
    score = 100.0 if total == 0 else round(100 * (1 - duplicates / total), 4)
    return score, duplicates, total


def _validity_masks(
    tables: dict[str, pd.DataFrame], *, silver: bool
) -> dict[str, pd.Series]:
    activities = tables["activities"]
    harvests = tables["harvests"]
    inventory = tables["inventory_transactions"]
    readings = tables["sensor_readings"]
    weather = tables["weather_daily"]
    health = tables["crop_health_observations"]

    if silver:
        activity_numeric = ("quantity_kg", "labor_hours", "material_cost_vnd", "labor_cost_vnd")
        harvest_numeric = ("harvest_quantity_kg", "waste_quantity_kg", "revenue_vnd")
        inventory_numeric = (
            "quantity_base_unit",
            "unit_cost_base_unit_vnd",
            "total_amount_vnd",
        )
        activity_invalid = activities[list(activity_numeric)].isna().any(axis=1) | (
            activities[list(activity_numeric)] < 0
        ).any(axis=1)
        harvest_invalid = harvests[list(harvest_numeric)].isna().any(axis=1) | (
            harvests[list(harvest_numeric)] < 0
        ).any(axis=1)
        inventory_invalid = inventory[list(inventory_numeric)].isna().any(axis=1) | (
            inventory[list(inventory_numeric)] < 0
        ).any(axis=1)
        inventory_invalid |= ~inventory["transaction_type"].isin(("IN", "OUT"))
    else:
        activity_numeric = ("quantity", "labor_hours", "material_cost_vnd", "labor_cost_vnd")
        harvest_numeric = ("quantity", "waste_quantity_kg", "revenue_vnd")
        inventory_numeric = ("quantity", "unit_cost_vnd", "total_amount_vnd")
        activity_invalid = activities[list(activity_numeric)].isna().any(axis=1) | (
            activities[list(activity_numeric)] < 0
        ).any(axis=1)
        harvest_invalid = harvests[list(harvest_numeric)].isna().any(axis=1) | (
            harvests[list(harvest_numeric)] < 0
        ).any(axis=1)
        inventory_invalid = inventory[list(inventory_numeric)].isna().any(axis=1) | (
            inventory[list(inventory_numeric)] < 0
        ).any(axis=1)
        canonical_units = {"kg", "liter", "piece"}
        activity_invalid |= ~activities["unit"].astype("string").str.lower().eq("kg")
        harvest_invalid |= ~harvests["unit"].astype("string").str.lower().eq("kg")
        inventory_invalid |= ~inventory["unit"].astype("string").str.lower().isin(
            canonical_units
        )
        inventory_invalid |= ~inventory["transaction_type"].astype("string").str.upper().isin(
            ("IN", "OUT")
        )

    reading_numeric = (
        "temperature_c",
        "air_humidity_pct",
        "soil_moisture_pct",
        "soil_ph",
        "rainfall_mm",
        "battery_pct",
    )
    reading_invalid = readings[list(reading_numeric)].isna().any(axis=1)
    reading_invalid |= ~readings["temperature_c"].between(-10, 60)
    reading_invalid |= ~readings["air_humidity_pct"].between(0, 100)
    reading_invalid |= ~readings["soil_moisture_pct"].between(0, 100)
    reading_invalid |= ~readings["soil_ph"].between(0, 14)
    reading_invalid |= ~readings["rainfall_mm"].between(0, 1_000)
    reading_invalid |= ~readings["battery_pct"].between(0, 100)

    weather_numeric = ("temperature_avg_c", "humidity_avg_pct", "rainfall_mm", "wind_speed_kph")
    weather_invalid = weather[list(weather_numeric)].isna().any(axis=1)
    weather_invalid |= ~weather["temperature_avg_c"].between(-10, 60)
    weather_invalid |= ~weather["humidity_avg_pct"].between(0, 100)
    weather_invalid |= ~weather["rainfall_mm"].between(0, 1_000)
    weather_invalid |= ~weather["wind_speed_kph"].between(0, 250)

    health_invalid = health[["affected_area_pct", "plant_mortality_pct"]].isna().any(axis=1)
    health_invalid |= ~health["affected_area_pct"].between(0, 100)
    health_invalid |= ~health["plant_mortality_pct"].between(0, 100)
    health_invalid |= ~health["severity"].astype("string").str.lower().isin(
        ("none", "low", "medium", "high")
    )

    return {
        "activities": activity_invalid,
        "harvests": harvest_invalid,
        "inventory_transactions": inventory_invalid,
        "sensor_readings": reading_invalid,
        "weather_daily": weather_invalid,
        "crop_health_observations": health_invalid,
    }


def _validity(tables: dict[str, pd.DataFrame], *, silver: bool) -> tuple[float, int, int]:
    masks = _validity_masks(tables, silver=silver)
    invalid = sum(int(mask.sum()) for mask in masks.values())
    total = sum(len(mask) for mask in masks.values())
    score = 100.0 if total == 0 else round(100 * (1 - invalid / total), 4)
    return score, invalid, total


def _freshness(tables: dict[str, pd.DataFrame], as_of_date: date) -> tuple[float, int | None]:
    timestamps: list[pd.Timestamp] = []
    for table, column in FRESHNESS_COLUMNS:
        parsed = pd.to_datetime(tables[table][column], errors="coerce")
        if parsed.notna().any():
            timestamps.append(parsed.max())
    if not timestamps:
        return 0.0, None
    newest = max(timestamps).date()
    age_days = max((as_of_date - newest).days, 0)
    score = max(0.0, round(100 - age_days * 1.5, 2))
    return score, age_days


def _checks(tables: dict[str, pd.DataFrame], *, silver: bool) -> list[dict[str, Any]]:
    checks: list[dict[str, Any]] = []
    for name, columns in REQUIRED_COLUMNS.items():
        failed = int(tables[name][list(columns)].isna().any(axis=1).sum())
        checks.append(
            {
                "table": name,
                "check": "required_fields_present",
                "failed_rows": failed,
                "total_rows": len(tables[name]),
                "severity": "error",
            }
        )
    for name, key in PRIMARY_KEYS.items():
        failed = int(tables[name].duplicated(key, keep="first").sum())
        checks.append(
            {
                "table": name,
                "check": f"unique_{key}",
                "failed_rows": failed,
                "total_rows": len(tables[name]),
                "severity": "error",
            }
        )
    for name, mask in _validity_masks(tables, silver=silver).items():
        checks.append(
            {
                "table": name,
                "check": "valid_ranges_and_canonical_units",
                "failed_rows": int(mask.sum()),
                "total_rows": len(mask),
                "severity": "error",
            }
        )
    return checks


def build_quality_report(
    raw: dict[str, pd.DataFrame],
    silver_tables: dict[str, pd.DataFrame],
    actions: dict[str, int],
    as_of_date: date,
) -> dict[str, Any]:
    before_uniqueness, _, _ = _uniqueness(raw)
    after_uniqueness, _, _ = _uniqueness(silver_tables)
    before_validity, _, _ = _validity(raw, silver=False)
    after_validity, _, _ = _validity(silver_tables, silver=True)
    before_freshness, before_age = _freshness(raw, as_of_date)
    after_freshness, after_age = _freshness(silver_tables, as_of_date)
    before_checks = _checks(raw, silver=False)
    after_checks = _checks(silver_tables, silver=True)
    status = "passed" if all(check["failed_rows"] == 0 for check in after_checks) else "failed"

    return {
        "status": status,
        "as_of_date": as_of_date.isoformat(),
        "scores": {
            "before": {
                "completeness_pct": _completeness(raw),
                "validity_pct": before_validity,
                "uniqueness_pct": before_uniqueness,
                "freshness_pct": before_freshness,
                "freshness_age_days": before_age,
            },
            "after": {
                "completeness_pct": _completeness(silver_tables),
                "validity_pct": after_validity,
                "uniqueness_pct": after_uniqueness,
                "freshness_pct": after_freshness,
                "freshness_age_days": after_age,
            },
        },
        "checks": {"before": before_checks, "after": after_checks},
        "remediation_actions": actions,
    }
