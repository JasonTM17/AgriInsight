from __future__ import annotations

from datetime import date, datetime

import pandas as pd


HISTORY_COLUMNS = (
    "harvest_id",
    "harvested_at",
    "farm_code",
    "field_code",
    "season_code",
    "crop_code",
    "season_start_date",
    "season_completed_at",
    "season_area_ha",
    "season_status",
    "harvest_quantity_kg",
)
CANDIDATE_COLUMNS = (
    "farm_code",
    "field_code",
    "season_code",
    "crop_code",
    "season_start_date",
    "expected_harvest_date",
    "season_area_ha",
    "season_status",
)


def history_row(
    year: int,
    index: int,
    yield_kg_per_ha: float,
    *,
    crop_code: str = "RICE",
    area_ha: float = 1.0,
    start_day: int | None = None,
) -> dict[str, object]:
    day = start_day or index
    season_code = f"SEASON-{year}-{index:04d}"
    completed_at = datetime(year, 6, index, 12)
    return {
        "harvest_id": f"HARVEST-{year}-{index:04d}-01",
        "harvested_at": completed_at.replace(hour=8).isoformat(),
        "farm_code": "FARM-001",
        "field_code": f"FIELD-{index:04d}",
        "season_code": season_code,
        "crop_code": crop_code,
        "season_start_date": date(year, 1, day).isoformat(),
        "season_completed_at": completed_at.isoformat(),
        "season_area_ha": area_ha,
        "season_status": "completed",
        "harvest_quantity_kg": yield_kg_per_ha * area_ha,
    }


def candidate(
    *,
    crop_code: str = "RICE",
    area_ha: float = 2.0,
    start_date: date = date(2026, 1, 1),
    expected_harvest_date: date = date(2026, 10, 1),
) -> dict[str, object]:
    return {
        "farm_code": "FARM-001",
        "field_code": "FIELD-9001",
        "season_code": "SEASON-2026-9001",
        "crop_code": crop_code,
        "season_start_date": start_date.isoformat(),
        "expected_harvest_date": expected_harvest_date.isoformat(),
        "season_area_ha": area_ha,
        "season_status": "active",
    }


def history_frame(rows: list[dict[str, object]]) -> pd.DataFrame:
    return pd.DataFrame(rows, columns=HISTORY_COLUMNS)


def candidate_frame(rows: list[dict[str, object]]) -> pd.DataFrame:
    return pd.DataFrame(rows, columns=CANDIDATE_COLUMNS)


def ready_history(crop_code: str = "RICE") -> list[dict[str, object]]:
    return [
        *[
            history_row(2024, index, value, crop_code=crop_code)
            for index, value in enumerate((100, 110, 120, 130, 140), start=1)
        ],
        *[
            history_row(2025, index, value, crop_code=crop_code)
            for index, value in enumerate((120, 130, 140, 150, 160), start=1)
        ],
    ]
