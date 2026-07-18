from __future__ import annotations

from dataclasses import dataclass

import pandas as pd

from agriinsight.transform_crop_health import clean_crop_health
from agriinsight.transform_inventory import clean_inventory


@dataclass
class TransformResult:
    silver: dict[str, pd.DataFrame]
    quarantine: dict[str, pd.DataFrame]
    actions: dict[str, int]


def _canonical_code(series: pd.Series) -> pd.Series:
    return series.astype("string").str.strip().str.upper()


def _quarantine_rows(frame: pd.DataFrame, mask: pd.Series, reason: str) -> pd.DataFrame:
    rejected = frame.loc[mask].copy()
    rejected["quarantine_reason"] = reason
    return rejected


def _combine_quarantine(parts: list[pd.DataFrame], columns: list[str]) -> pd.DataFrame:
    non_empty = [part for part in parts if not part.empty]
    if not non_empty:
        return pd.DataFrame(columns=[*columns, "quarantine_reason"])
    return pd.concat(non_empty, ignore_index=True)


def clean_bronze(raw: dict[str, pd.DataFrame]) -> TransformResult:
    """Normalize source data and quarantine rows that cannot be repaired safely."""

    actions: dict[str, int] = {
        "codes_canonicalized": 0,
        "units_converted_to_kg": 0,
        "duplicates_removed": 0,
        "rows_quarantined": 0,
    }
    quarantine: dict[str, pd.DataFrame] = {}

    farms = raw["farms"].copy()
    original_farm_codes = farms["farm_code"].astype("string")
    farms["farm_code"] = _canonical_code(farms["farm_code"])
    actions["codes_canonicalized"] += int((original_farm_codes != farms["farm_code"]).sum())
    farms["farm_name"] = farms["farm_name"].astype("string").str.strip()
    farms["province"] = farms["province"].astype("string").str.strip()
    for column in ("registered_area_ha", "latitude", "longitude"):
        farms[column] = pd.to_numeric(farms[column], errors="coerce")
    farm_duplicate = farms.duplicated("farm_code", keep="first")
    farm_invalid = (
        farms["farm_code"].isna()
        | farms["farm_name"].isna()
        | farms["registered_area_ha"].isna()
        | (farms["registered_area_ha"] <= 0)
    )
    farm_quarantine = [
        _quarantine_rows(farms, farm_duplicate, "duplicate_primary_key"),
        _quarantine_rows(farms, farm_invalid & ~farm_duplicate, "invalid_required_value"),
    ]
    farms = farms.loc[~farm_duplicate & ~farm_invalid].reset_index(drop=True)
    quarantine["farms"] = _combine_quarantine(farm_quarantine, list(raw["farms"].columns))

    fields = raw["fields"].copy()
    for column in ("field_code", "farm_code"):
        original = fields[column].astype("string")
        fields[column] = _canonical_code(fields[column])
        actions["codes_canonicalized"] += int((original != fields[column]).sum())
    for column in ("area_ha", "latitude", "longitude"):
        fields[column] = pd.to_numeric(fields[column], errors="coerce")
    field_duplicate = fields.duplicated("field_code", keep="first")
    field_invalid = (
        fields["field_code"].isna()
        | ~fields["farm_code"].isin(farms["farm_code"])
        | fields["area_ha"].isna()
        | (fields["area_ha"] <= 0)
    )
    field_quarantine = [
        _quarantine_rows(fields, field_duplicate, "duplicate_primary_key"),
        _quarantine_rows(fields, field_invalid & ~field_duplicate, "invalid_dimension_reference"),
    ]
    fields = fields.loc[~field_duplicate & ~field_invalid].reset_index(drop=True)
    quarantine["fields"] = _combine_quarantine(field_quarantine, list(raw["fields"].columns))

    crops = raw["crops"].copy()
    original_crop_codes = crops["crop_code"].astype("string")
    crops["crop_code"] = _canonical_code(crops["crop_code"])
    actions["codes_canonicalized"] += int((original_crop_codes != crops["crop_code"]).sum())
    crop_duplicate = crops.duplicated("crop_code", keep="first")
    crop_invalid = crops[["crop_code", "crop_name", "category"]].isna().any(axis=1)
    crop_quarantine = [
        _quarantine_rows(crops, crop_duplicate, "duplicate_primary_key"),
        _quarantine_rows(crops, crop_invalid & ~crop_duplicate, "invalid_required_value"),
    ]
    crops = crops.loc[~crop_duplicate & ~crop_invalid].reset_index(drop=True)
    quarantine["crops"] = _combine_quarantine(crop_quarantine, list(raw["crops"].columns))

    seasons = raw["seasons"].copy()
    for column in ("season_code", "field_code", "crop_code"):
        original = seasons[column].astype("string")
        seasons[column] = _canonical_code(seasons[column])
        actions["codes_canonicalized"] += int((original != seasons[column]).sum())
    for column in ("start_date", "expected_harvest_date"):
        seasons[column] = pd.to_datetime(seasons[column], errors="coerce").dt.date.astype("string")
    for column in ("target_yield_kg", "budget_cost_vnd"):
        seasons[column] = pd.to_numeric(seasons[column], errors="coerce")
    season_duplicate = seasons.duplicated("season_code", keep="first")
    season_invalid = (
        seasons["season_code"].isna()
        | ~seasons["field_code"].isin(fields["field_code"])
        | ~seasons["crop_code"].isin(crops["crop_code"])
        | seasons["start_date"].isna()
        | seasons["expected_harvest_date"].isna()
        | (seasons["target_yield_kg"] <= 0)
        | (seasons["budget_cost_vnd"] <= 0)
    )
    season_quarantine = [
        _quarantine_rows(seasons, season_duplicate, "duplicate_primary_key"),
        _quarantine_rows(seasons, season_invalid & ~season_duplicate, "invalid_dimension_reference"),
    ]
    seasons = seasons.loc[~season_duplicate & ~season_invalid].reset_index(drop=True)
    quarantine["seasons"] = _combine_quarantine(season_quarantine, list(raw["seasons"].columns))

    activities = raw["activities"].copy()
    for column in ("activity_id", "farm_code", "field_code", "season_code"):
        original = activities[column].astype("string")
        activities[column] = _canonical_code(activities[column])
        actions["codes_canonicalized"] += int((original != activities[column]).sum())
    activities["occurred_at"] = pd.to_datetime(activities["occurred_at"], errors="coerce")
    for column in ("quantity", "labor_hours", "material_cost_vnd", "labor_cost_vnd"):
        activities[column] = pd.to_numeric(activities[column], errors="coerce")
    normalized_unit = activities["unit"].astype("string").str.strip().str.lower()
    tonne_mask = normalized_unit.isin(("tonne", "ton", "t"))
    actions["units_converted_to_kg"] += int(tonne_mask.sum())
    activities["quantity_kg"] = activities["quantity"].where(~tonne_mask, activities["quantity"] * 1_000)
    activities["unit"] = normalized_unit.where(~tonne_mask, "kg")
    duplicate_activity = activities.duplicated("activity_id", keep="first")
    actions["duplicates_removed"] += int(duplicate_activity.sum())
    valid_farm_field = set(zip(fields["farm_code"], fields["field_code"], strict=False))
    valid_activity_relation = pd.Series(
        [
            (farm_code, field_code) in valid_farm_field
            for farm_code, field_code in zip(
                activities["farm_code"], activities["field_code"], strict=False
            )
        ],
        index=activities.index,
    )
    activity_invalid = (
        activities["activity_id"].isna()
        | activities["occurred_at"].isna()
        | ~activities["season_code"].isin(seasons["season_code"])
        | ~valid_activity_relation
        | ~activities["unit"].eq("kg")
        | activities[["quantity_kg", "labor_hours", "material_cost_vnd", "labor_cost_vnd"]]
        .isna()
        .any(axis=1)
        | (activities[["quantity_kg", "labor_hours", "material_cost_vnd", "labor_cost_vnd"]] < 0)
        .any(axis=1)
    )
    activity_quarantine = [
        _quarantine_rows(activities, duplicate_activity, "duplicate_primary_key"),
        _quarantine_rows(
            activities,
            activity_invalid & ~duplicate_activity,
            "invalid_fact_value_or_reference",
        ),
    ]
    activities = activities.loc[~duplicate_activity & ~activity_invalid].copy()
    activities["occurred_at"] = activities["occurred_at"].dt.strftime("%Y-%m-%dT%H:%M:%S")
    activities["total_cost_vnd"] = (
        activities["material_cost_vnd"] + activities["labor_cost_vnd"]
    )
    activities = activities.drop(columns=["quantity", "unit"]).reset_index(drop=True)
    quarantine["activities"] = _combine_quarantine(
        activity_quarantine, list(raw["activities"].columns)
    )

    harvests = raw["harvests"].copy()
    for column in ("harvest_id", "farm_code", "field_code", "season_code", "crop_code"):
        original = harvests[column].astype("string")
        harvests[column] = _canonical_code(harvests[column])
        actions["codes_canonicalized"] += int((original != harvests[column]).sum())
    harvests["harvested_at"] = pd.to_datetime(harvests["harvested_at"], errors="coerce")
    for column in ("quantity", "waste_quantity_kg", "revenue_vnd"):
        harvests[column] = pd.to_numeric(harvests[column], errors="coerce")
    normalized_harvest_unit = harvests["unit"].astype("string").str.strip().str.lower()
    harvest_tonne_mask = normalized_harvest_unit.isin(("tonne", "ton", "t"))
    actions["units_converted_to_kg"] += int(harvest_tonne_mask.sum())
    harvests["harvest_quantity_kg"] = harvests["quantity"].where(
        ~harvest_tonne_mask, harvests["quantity"] * 1_000
    )
    harvests["unit"] = normalized_harvest_unit.where(~harvest_tonne_mask, "kg")
    duplicate_harvest = harvests.duplicated("harvest_id", keep="first")
    actions["duplicates_removed"] += int(duplicate_harvest.sum())
    valid_crop_season = set(
        zip(seasons["season_code"], seasons["field_code"], seasons["crop_code"], strict=False)
    )
    valid_harvest_relation = pd.Series(
        [
            (season_code, field_code, crop_code) in valid_crop_season
            for season_code, field_code, crop_code in zip(
                harvests["season_code"],
                harvests["field_code"],
                harvests["crop_code"],
                strict=False,
            )
        ],
        index=harvests.index,
    )
    field_farm_lookup = fields.set_index("field_code")["farm_code"].to_dict()
    valid_harvest_farm = harvests.apply(
        lambda row: field_farm_lookup.get(row["field_code"]) == row["farm_code"], axis=1
    )
    harvest_invalid = (
        harvests["harvest_id"].isna()
        | harvests["harvested_at"].isna()
        | ~valid_harvest_relation
        | ~valid_harvest_farm
        | ~harvests["unit"].eq("kg")
        | harvests[["harvest_quantity_kg", "waste_quantity_kg", "revenue_vnd"]]
        .isna()
        .any(axis=1)
        | (harvests[["harvest_quantity_kg", "waste_quantity_kg", "revenue_vnd"]] < 0).any(axis=1)
        | (harvests["waste_quantity_kg"] > harvests["harvest_quantity_kg"])
    )
    harvest_quarantine = [
        _quarantine_rows(harvests, duplicate_harvest, "duplicate_primary_key"),
        _quarantine_rows(
            harvests,
            harvest_invalid & ~duplicate_harvest,
            "invalid_fact_value_or_reference",
        ),
    ]
    harvests = harvests.loc[~duplicate_harvest & ~harvest_invalid].copy()
    harvests["harvested_at"] = harvests["harvested_at"].dt.strftime("%Y-%m-%dT%H:%M:%S")
    harvests = harvests.drop(columns=["quantity", "unit"]).reset_index(drop=True)
    quarantine["harvests"] = _combine_quarantine(
        harvest_quarantine, list(raw["harvests"].columns)
    )

    silver = {
        "farms": farms,
        "fields": fields,
        "crops": crops,
        "seasons": seasons,
        "activities": activities,
        "harvests": harvests,
    }
    inventory_result = clean_inventory(raw, set(farms["farm_code"]))
    health_result = clean_crop_health(raw, farms, fields, seasons)
    for domain_result in (inventory_result, health_result):
        silver.update(domain_result.silver)
        quarantine.update(domain_result.quarantine)
        for action, count in domain_result.actions.items():
            actions[action] = actions.get(action, 0) + count

    actions["rows_quarantined"] = sum(len(frame) for frame in quarantine.values())
    return TransformResult(silver=silver, quarantine=quarantine, actions=actions)
