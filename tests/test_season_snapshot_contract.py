from __future__ import annotations

from datetime import date

import pandas as pd

from agriinsight.config import GenerationConfig
from agriinsight.synthetic import generate_bronze
from agriinsight.transform import clean_bronze


def _season_contract_config() -> GenerationConfig:
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


def _with_snapshot_columns(raw: dict[str, pd.DataFrame]) -> dict[str, pd.DataFrame]:
    tables = {name: frame.copy() for name, frame in raw.items()}
    fields = tables["fields"].set_index("field_code")
    seasons = tables["seasons"].copy()
    seasons["season_area_ha"] = seasons["field_code"].map(fields["area_ha"])
    completion = pd.Series(pd.NaT, index=seasons.index, dtype="datetime64[ns]")
    completed = seasons["status"].astype("string").str.lower().eq("completed")
    completion.loc[completed] = pd.to_datetime(
        seasons.loc[completed, "expected_harvest_date"], errors="coerce"
    ) + pd.Timedelta(hours=18)
    seasons["completed_at"] = completion.dt.strftime("%Y-%m-%dT%H:%M:%S")
    seasons.loc[~completed, "completed_at"] = None
    tables["seasons"] = seasons
    return tables


def test_generate_bronze_emits_2024_season_snapshot_contract() -> None:
    raw = generate_bronze(_season_contract_config())
    seasons = raw["seasons"]

    assert {"season_area_ha", "completed_at"}.issubset(seasons.columns)
    years = set(pd.to_datetime(seasons["start_date"], errors="raise").dt.year.tolist())
    assert {2024, 2025, 2026}.issubset(years)

    completed = seasons["status"].astype("string").str.lower().eq("completed")
    active = seasons["status"].astype("string").str.lower().eq("active")
    assert completed.any()
    assert active.any()
    assert seasons.loc[completed, "season_area_ha"].gt(0).all()
    assert seasons.loc[completed, "completed_at"].notna().all()
    assert seasons.loc[active, "completed_at"].isna().all()


def test_clean_bronze_quarantines_invalid_snapshot_rows_and_harvest_timing() -> None:
    raw = _with_snapshot_columns(generate_bronze(_season_contract_config()))
    seasons = raw["seasons"].copy()
    completed_codes = seasons.loc[
        seasons["status"].astype("string").str.lower().eq("completed"),
        "season_code",
    ].tolist()
    active_code = seasons.loc[
        seasons["status"].astype("string").str.lower().eq("active"),
        "season_code",
    ].iloc[0]
    invalid_area_code, missing_completion_code, late_completion_code = completed_codes[:3]

    seasons.loc[seasons["season_code"] == invalid_area_code, "season_area_ha"] = 0
    seasons.loc[seasons["season_code"] == missing_completion_code, "completed_at"] = None
    raw["seasons"] = seasons

    season_rows = seasons.set_index("season_code")
    fields = raw["fields"].set_index("field_code")
    harvests = raw["harvests"].copy()

    active_row = season_rows.loc[active_code]
    active_harvest = harvests.iloc[[0]].copy()
    active_harvest.loc[:, "harvest_id"] = "HARVEST-ACTIVE-SEASON"
    active_harvest.loc[:, "farm_code"] = fields.loc[active_row["field_code"], "farm_code"]
    active_harvest.loc[:, "field_code"] = active_row["field_code"]
    active_harvest.loc[:, "season_code"] = active_code
    active_harvest.loc[:, "crop_code"] = active_row["crop_code"]
    active_harvest.loc[:, "harvested_at"] = "2026-03-20T08:00:00"

    late_row = season_rows.loc[late_completion_code]
    late_harvest = harvests.iloc[[1]].copy()
    late_harvest.loc[:, "harvest_id"] = "HARVEST-AFTER-COMPLETION"
    late_harvest.loc[:, "farm_code"] = fields.loc[late_row["field_code"], "farm_code"]
    late_harvest.loc[:, "field_code"] = late_row["field_code"]
    late_harvest.loc[:, "season_code"] = late_completion_code
    late_harvest.loc[:, "crop_code"] = late_row["crop_code"]
    late_harvest.loc[:, "harvested_at"] = (
        pd.Timestamp(late_row["completed_at"]) + pd.Timedelta(hours=1)
    ).strftime("%Y-%m-%dT%H:%M:%S")

    raw["harvests"] = pd.concat(
        [harvests, active_harvest, late_harvest],
        ignore_index=True,
    )

    result = clean_bronze(raw)

    quarantined_seasons = set(result.quarantine["seasons"]["season_code"].dropna())
    assert invalid_area_code in quarantined_seasons
    assert missing_completion_code in quarantined_seasons
    assert invalid_area_code not in set(result.silver["seasons"]["season_code"])
    assert missing_completion_code not in set(result.silver["seasons"]["season_code"])

    quarantined_harvests = set(result.quarantine["harvests"]["harvest_id"].dropna())
    assert "HARVEST-ACTIVE-SEASON" in quarantined_harvests
    assert "HARVEST-AFTER-COMPLETION" in quarantined_harvests
    assert "HARVEST-ACTIVE-SEASON" not in set(result.silver["harvests"]["harvest_id"])
    assert "HARVEST-AFTER-COMPLETION" not in set(result.silver["harvests"]["harvest_id"])
