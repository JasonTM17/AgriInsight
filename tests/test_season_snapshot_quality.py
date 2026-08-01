from __future__ import annotations

import pandas as pd
import pytest

from agriinsight.quality import build_quality_report
from agriinsight.synthetic import generate_bronze
from agriinsight.transform import clean_bronze
from agriinsight.warehouse import _validate_harvest_contract, _validate_season_contract
from tests.test_season_snapshot_contract import (
    _season_contract_config,
    _with_snapshot_columns,
)


def test_quality_report_handles_duplicate_season_keys_without_crashing() -> None:
    raw = generate_bronze(_season_contract_config())
    raw["seasons"] = pd.concat(
        [raw["seasons"], raw["seasons"].iloc[[0]]],
        ignore_index=True,
    )
    transformed = clean_bronze(raw)

    report = build_quality_report(
        raw,
        transformed.silver,
        transformed.actions,
        _season_contract_config().as_of_date,
    )

    duplicate_check = next(
        check
        for check in report["checks"]["before"]
        if check["table"] == "seasons"
        and check["check"] == "unique_season_code"
    )
    assert duplicate_check["failed_rows"] == 1
    assert report["status"] == "passed"


def test_quality_report_handles_missing_completion_column_without_crashing() -> None:
    raw = generate_bronze(_season_contract_config())
    raw["seasons"] = raw["seasons"].drop(columns=["completed_at"])
    transformed = clean_bronze(raw)

    report = build_quality_report(
        raw,
        transformed.silver,
        transformed.actions,
        _season_contract_config().as_of_date,
    )

    season_validity = next(
        check
        for check in report["checks"]["before"]
        if check["table"] == "seasons"
        and check["check"] == "valid_ranges_and_canonical_units"
    )
    assert season_validity["failed_rows"] > 0
    assert report["status"] == "passed"


@pytest.mark.parametrize(
    "missing_column",
    ("season_area_ha", "expected_harvest_date", "status"),
)
def test_quality_report_handles_missing_snapshot_columns(
    missing_column: str,
) -> None:
    raw = generate_bronze(_season_contract_config())
    raw["seasons"] = raw["seasons"].drop(columns=[missing_column])
    transformed = clean_bronze(raw)

    report = build_quality_report(
        raw,
        transformed.silver,
        transformed.actions,
        _season_contract_config().as_of_date,
    )

    required_check = next(
        check
        for check in report["checks"]["before"]
        if check["table"] == "seasons"
        and check["check"] == "required_fields_present"
    )
    assert required_check["failed_rows"] > 0


def test_snapshot_boundaries_reject_non_finite_values_and_timestamps() -> None:
    raw = _with_snapshot_columns(generate_bronze(_season_contract_config()))
    completed_code = raw["seasons"].loc[
        raw["seasons"]["status"].eq("completed"), "season_code"
    ].iloc[0]
    raw["seasons"].loc[
        raw["seasons"]["season_code"] == completed_code, "season_area_ha"
    ] = float("inf")
    raw["seasons"].loc[
        raw["seasons"]["season_code"] == completed_code, "completed_at"
    ] = "2025-05-10T18:00:00Z"
    raw["harvests"].loc[0, "quantity"] = float("inf")

    transformed = clean_bronze(raw)
    quarantined_seasons = set(transformed.quarantine["seasons"]["season_code"])
    assert completed_code in quarantined_seasons

    report = build_quality_report(
        raw,
        transformed.silver,
        transformed.actions,
        _season_contract_config().as_of_date,
    )
    season_validity = next(
        check
        for check in report["checks"]["before"]
        if check["table"] == "seasons"
        and check["check"] == "valid_ranges_and_canonical_units"
    )
    assert season_validity["failed_rows"] > 0

    valid = clean_bronze(_with_snapshot_columns(generate_bronze(_season_contract_config())))
    invalid_area = {name: frame.copy() for name, frame in valid.silver.items()}
    invalid_area["seasons"].loc[0, "season_area_ha"] = float("inf")
    with pytest.raises(ValueError, match="season snapshot"):
        _validate_season_contract(invalid_area)

    invalid_harvest = {name: frame.copy() for name, frame in valid.silver.items()}
    invalid_harvest["harvests"].loc[0, "harvest_quantity_kg"] = float("inf")
    with pytest.raises(ValueError, match="harvest"):
        _validate_harvest_contract(invalid_harvest)


def test_snapshot_boundaries_reject_impossible_active_completion_timestamp() -> None:
    raw = _with_snapshot_columns(generate_bronze(_season_contract_config()))
    active_code = raw["seasons"].loc[
        raw["seasons"]["status"].eq("active"), "season_code"
    ].iloc[0]
    raw["seasons"].loc[
        raw["seasons"]["season_code"] == active_code, "completed_at"
    ] = "2025-02-31T00:00:00"

    transformed = clean_bronze(raw)
    assert active_code in set(transformed.quarantine["seasons"]["season_code"])

    report = build_quality_report(
        raw,
        transformed.silver,
        transformed.actions,
        _season_contract_config().as_of_date,
    )
    season_validity = next(
        check
        for check in report["checks"]["before"]
        if check["table"] == "seasons"
        and check["check"] == "valid_ranges_and_canonical_units"
    )
    assert season_validity["failed_rows"] > 0

    valid = clean_bronze(_with_snapshot_columns(generate_bronze(_season_contract_config())))
    invalid_completion = {name: frame.copy() for name, frame in valid.silver.items()}
    invalid_completion["seasons"].loc[
        invalid_completion["seasons"]["season_code"] == active_code,
        "completed_at",
    ] = "2025-02-31T00:00:00"
    with pytest.raises(ValueError, match="season snapshot"):
        _validate_season_contract(invalid_completion)


def test_harvest_timestamp_with_timezone_is_quarantined() -> None:
    raw = _with_snapshot_columns(generate_bronze(_season_contract_config()))
    harvest_id = raw["harvests"].iloc[0]["harvest_id"]
    raw["harvests"].loc[0, "harvested_at"] = (
        pd.Timestamp(raw["harvests"].loc[0, "harvested_at"])
        .tz_localize("UTC")
        .isoformat()
    )

    transformed = clean_bronze(raw)

    assert harvest_id in set(transformed.quarantine["harvests"]["harvest_id"])
