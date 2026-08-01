from __future__ import annotations

import sqlite3
from datetime import date


AS_OF_DATE = date(2026, 6, 29)


def yield_forecast_warehouse() -> sqlite3.Connection:
    connection = sqlite3.connect(":memory:")
    connection.executescript(
        """
        CREATE TABLE dim_farm (farm_key INTEGER PRIMARY KEY, farm_code TEXT NOT NULL);
        CREATE TABLE dim_field (
            field_key INTEGER PRIMARY KEY,
            farm_key INTEGER NOT NULL,
            field_code TEXT NOT NULL
        );
        CREATE TABLE dim_crop (crop_key INTEGER PRIMARY KEY, crop_code TEXT NOT NULL);
        CREATE TABLE dim_season (
            season_key INTEGER PRIMARY KEY,
            season_code TEXT NOT NULL,
            farm_key INTEGER NOT NULL,
            field_key INTEGER NOT NULL,
            crop_key INTEGER NOT NULL,
            start_date TEXT NOT NULL,
            expected_harvest_date TEXT NOT NULL,
            season_area_ha REAL NOT NULL,
            target_yield_kg REAL,
            completed_at TEXT,
            status TEXT NOT NULL
        );
        CREATE TABLE fact_harvest (
            harvest_id TEXT PRIMARY KEY,
            farm_key INTEGER NOT NULL,
            field_key INTEGER NOT NULL,
            crop_key INTEGER NOT NULL,
            season_key INTEGER NOT NULL,
            harvested_at TEXT NOT NULL,
            harvest_quantity_kg REAL NOT NULL
        );
        INSERT INTO dim_farm VALUES (1, 'FARM-001'), (2, 'FARM-002');
        INSERT INTO dim_field VALUES (1, 1, 'FIELD-001'), (2, 2, 'FIELD-002');
        INSERT INTO dim_crop VALUES (1, 'RICE');
        """
    )
    seasons = [
        (
            year - 2020,
            f"RICE-{year}",
            1,
            1,
            1,
            f"{year}-03-01",
            f"{year}-10-01",
            2.0,
            float(year * 10),
            f"{year}-10-02T12:00:00",
            "completed",
        )
        for year in range(2021, 2026)
    ]
    seasons.append(
        (
            6,
            "RICE-2026",
            1,
            1,
            1,
            "2026-03-01",
            "2026-10-01",
            2.0,
            20260.0,
            None,
            "active",
        )
    )
    connection.executemany(
        "INSERT INTO dim_season VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        seasons,
    )
    harvests = [
        (
            f"HARVEST-{year}",
            1,
            1,
            1,
            year - 2020,
            f"{year}-09-30T08:00:00",
            float((year - 2020) * 2),
        )
        for year in range(2021, 2025)
    ]
    harvests.extend(
        [
            ("HARVEST-2025-A", 1, 1, 1, 5, "2025-09-28T08:00:00", 7.0),
            ("HARVEST-2025-B", 1, 1, 1, 5, "2025-09-30T08:00:00", 3.0),
        ]
    )
    connection.executemany(
        "INSERT INTO fact_harvest VALUES (?, ?, ?, ?, ?, ?, ?)",
        harvests,
    )
    connection.commit()
    return connection
