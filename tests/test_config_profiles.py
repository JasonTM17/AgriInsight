from datetime import date

import pytest

from agriinsight.config import (
    DEFAULT_AS_OF_DATE,
    DEFAULT_SEED,
    GenerationConfig,
    resolve_generation_config,
)


def test_standard_profile_preserves_existing_defaults() -> None:
    config = resolve_generation_config()

    assert config.scale_profile == "standard"
    assert config.resolved_dimensions() == {
        "farm_count": 6,
        "fields_per_farm": 4,
        "activities_per_season": 14,
        "material_count": 15,
        "sensor_history_days": 120,
        "sensor_readings_per_day": 4,
    }


def test_big_data_profile_has_million_row_nominal_sensor_plan() -> None:
    config = resolve_generation_config("big-data")
    standard = resolve_generation_config()

    assert config.scale_profile == "big-data"
    assert config.nominal_sensor_readings == 1_051_200
    assert config.farm_count == 10
    assert config.fields_per_farm == 12
    assert config.sensor_history_days == 365
    assert config.sensor_readings_per_day == 24
    assert config.run_id.startswith(
        "synthetic-2026-07-18-20260718-big-data-"
    )
    assert config.run_id != standard.run_id


def test_explicit_profile_override_wins_and_preserves_seed_date() -> None:
    config = resolve_generation_config(
        "big-data",
        seed=123,
        as_of_date=date(2026, 7, 22),
        overrides={"farm_count": 2, "sensor_readings_per_day": 1},
    )

    assert config.seed == 123
    assert config.as_of_date == date(2026, 7, 22)
    assert config.farm_count == 2
    assert config.fields_per_farm == 12
    assert config.sensor_readings_per_day == 1


def test_run_id_covers_explicit_dimension_overrides() -> None:
    default = resolve_generation_config()
    overridden = resolve_generation_config(overrides={"farm_count": 5})

    assert default.run_id != overridden.run_id


def test_profile_rejects_unknown_override_and_profile() -> None:
    with pytest.raises(ValueError, match="unknown scale profile"):
        resolve_generation_config("large")
    with pytest.raises(ValueError, match="unknown GenerationConfig override"):
        resolve_generation_config(overrides={"unknown": 1})
    with pytest.raises(ValueError, match="scale_profile"):
        GenerationConfig(scale_profile="large")


def test_default_constants_remain_stable() -> None:
    assert DEFAULT_SEED == 20_260_718
    assert DEFAULT_AS_OF_DATE == date(2026, 7, 18)
