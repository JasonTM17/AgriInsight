from __future__ import annotations

from datetime import date

import pandas as pd

from agriinsight.yield_forecast_input_validation import (
    YieldForecastError,
    dates,
    numbers,
    required_frame,
    timestamps,
    validate_identifiers,
    validate_season_context,
    validate_status,
)

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

def validated_forecast_inputs(
    history: pd.DataFrame,
    candidates: pd.DataFrame,
    as_of_date: date,
) -> tuple[pd.DataFrame, pd.DataFrame]:
    if type(as_of_date) is not date:
        raise YieldForecastError("as_of_date must be a date")
    facts = required_frame(history, HISTORY_COLUMNS, "history")
    active = required_frame(candidates, CANDIDATE_COLUMNS, "candidate")
    cutoff_exclusive = pd.Timestamp(as_of_date) + pd.Timedelta(days=1)

    if not facts.empty:
        facts["harvested_at"] = timestamps(facts["harvested_at"])
        facts["season_completed_at"] = timestamps(facts["season_completed_at"])
        facts = facts[
            (facts["harvested_at"] < cutoff_exclusive)
            & (facts["season_completed_at"] < cutoff_exclusive)
        ].copy()
        if not facts.empty:
            facts["season_start_date"] = dates(facts["season_start_date"])
            if bool(
                (
                    (facts["harvested_at"] < facts["season_start_date"])
                    | (facts["harvested_at"] > facts["season_completed_at"])
                    | (
                        facts["season_start_date"]
                        >= facts["season_completed_at"].dt.normalize()
                    )
                ).any()
            ):
                raise YieldForecastError(
                    "harvest events require valid season completion chronology"
                )
            validate_identifiers(
                facts,
                ("harvest_id", "farm_code", "field_code", "season_code", "crop_code"),
            )
            validate_status(facts, "completed")
            facts["season_area_ha"] = numbers(
                facts["season_area_ha"], positive=True, label="season_area_ha"
            )
            facts["harvest_quantity_kg"] = numbers(
                facts["harvest_quantity_kg"],
                positive=False,
                label="harvest_quantity_kg",
            )
            if bool(facts["harvest_id"].duplicated().any()):
                raise YieldForecastError("harvest_id values must be unique")
            validate_season_context(facts)

    if not active.empty:
        active["season_start_date"] = dates(active["season_start_date"])
        active["expected_harvest_date"] = dates(active["expected_harvest_date"])
        validate_identifiers(
            active,
            ("farm_code", "field_code", "season_code", "crop_code"),
        )
        validate_status(active, "active")
        active["season_area_ha"] = numbers(
            active["season_area_ha"], positive=True, label="season_area_ha"
        )
        cutoff = pd.Timestamp(as_of_date)
        invalid_chronology = (
            (active["season_start_date"] > cutoff)
            | (active["expected_harvest_date"] <= cutoff)
            | (
                active["season_start_date"]
                >= active["expected_harvest_date"]
            )
        )
        if bool(invalid_chronology.any()):
            raise YieldForecastError("active candidate chronology is invalid")
        if bool(active["season_code"].duplicated().any()):
            raise YieldForecastError("candidate season_code values must be unique")

    overlap = set(facts["season_code"]) & set(active["season_code"])
    if overlap:
        raise YieldForecastError(
            "a season cannot be both active and completed forecast history"
        )
    return facts, active
