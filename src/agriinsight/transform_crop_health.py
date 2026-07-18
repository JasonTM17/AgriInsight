from __future__ import annotations

import pandas as pd

from agriinsight.domain_transform import DomainTransformResult


def _code(series: pd.Series) -> pd.Series:
    return series.astype("string").str.strip().str.upper()


def _reject(frame: pd.DataFrame, mask: pd.Series, reason: str) -> pd.DataFrame:
    rejected = frame.loc[mask].copy()
    rejected["quarantine_reason"] = reason
    return rejected


def _combine(parts: list[pd.DataFrame], columns: list[str]) -> pd.DataFrame:
    populated = [part for part in parts if not part.empty]
    if not populated:
        return pd.DataFrame(columns=[*columns, "quarantine_reason"])
    return pd.concat(populated, ignore_index=True)


def clean_crop_health(
    raw: dict[str, pd.DataFrame],
    farms: pd.DataFrame,
    fields: pd.DataFrame,
    seasons: pd.DataFrame,
) -> DomainTransformResult:
    actions = {"codes_canonicalized": 0, "duplicates_removed": 0}
    quarantine: dict[str, pd.DataFrame] = {}
    farm_codes = set(farms["farm_code"])
    field_farm_lookup = fields.set_index("field_code")["farm_code"].to_dict()

    sensors = raw["sensors"].copy()
    for column in ("sensor_code", "field_code"):
        original = sensors[column].astype("string")
        sensors[column] = _code(sensors[column])
        actions["codes_canonicalized"] += int((original != sensors[column]).sum())
    sensors["installed_at"] = pd.to_datetime(sensors["installed_at"], errors="coerce")
    sensors["status"] = sensors["status"].astype("string").str.strip().str.lower()
    duplicate = sensors.duplicated("sensor_code", keep="first")
    invalid = (
        sensors[["sensor_code", "field_code", "sensor_model"]].isna().any(axis=1)
        | ~sensors["field_code"].isin(fields["field_code"])
        | sensors["installed_at"].isna()
        | ~sensors["status"].isin(("active", "maintenance", "retired"))
    )
    quarantine["sensors"] = _combine(
        [
            _reject(sensors, duplicate, "duplicate_primary_key"),
            _reject(sensors, invalid & ~duplicate, "invalid_dimension_reference"),
        ],
        list(raw["sensors"].columns),
    )
    actions["duplicates_removed"] += int(duplicate.sum())
    sensors = sensors.loc[~duplicate & ~invalid].copy()
    sensors["installed_at"] = sensors["installed_at"].dt.strftime("%Y-%m-%d")
    sensors = sensors.reset_index(drop=True)

    readings = raw["sensor_readings"].copy()
    for column in ("reading_id", "sensor_code", "farm_code", "field_code"):
        original = readings[column].astype("string")
        readings[column] = _code(readings[column])
        actions["codes_canonicalized"] += int((original != readings[column]).sum())
    readings["observed_at"] = pd.to_datetime(readings["observed_at"], errors="coerce")
    reading_numeric = (
        "temperature_c",
        "air_humidity_pct",
        "soil_moisture_pct",
        "soil_ph",
        "rainfall_mm",
        "battery_pct",
    )
    for column in reading_numeric:
        readings[column] = pd.to_numeric(readings[column], errors="coerce")
    duplicate = readings.duplicated("reading_id", keep="first")
    actions["duplicates_removed"] += int(duplicate.sum())
    sensor_field_lookup = sensors.set_index("sensor_code")["field_code"].to_dict()
    valid_relation = readings.apply(
        lambda row: (
            sensor_field_lookup.get(row["sensor_code"]) == row["field_code"]
            and field_farm_lookup.get(row["field_code"]) == row["farm_code"]
        ),
        axis=1,
    )
    invalid = (
        readings["reading_id"].isna()
        | readings["observed_at"].isna()
        | ~valid_relation
        | readings[list(reading_numeric)].isna().any(axis=1)
        | ~readings["temperature_c"].between(-10, 60)
        | ~readings["air_humidity_pct"].between(0, 100)
        | ~readings["soil_moisture_pct"].between(0, 100)
        | ~readings["soil_ph"].between(0, 14)
        | ~readings["rainfall_mm"].between(0, 1_000)
        | ~readings["battery_pct"].between(0, 100)
    )
    quarantine["sensor_readings"] = _combine(
        [
            _reject(readings, duplicate, "duplicate_primary_key"),
            _reject(readings, invalid & ~duplicate, "invalid_fact_value_or_reference"),
        ],
        list(raw["sensor_readings"].columns),
    )
    readings = readings.loc[~duplicate & ~invalid].copy()
    readings["observed_at"] = readings["observed_at"].dt.strftime("%Y-%m-%dT%H:%M:%S")
    readings = readings.reset_index(drop=True)

    weather = raw["weather_daily"].copy()
    for column in ("weather_id", "farm_code"):
        original = weather[column].astype("string")
        weather[column] = _code(weather[column])
        actions["codes_canonicalized"] += int((original != weather[column]).sum())
    weather["weather_date"] = pd.to_datetime(weather["weather_date"], errors="coerce")
    weather_numeric = (
        "temperature_avg_c",
        "humidity_avg_pct",
        "rainfall_mm",
        "wind_speed_kph",
    )
    for column in weather_numeric:
        weather[column] = pd.to_numeric(weather[column], errors="coerce")
    duplicate = weather.duplicated("weather_id", keep="first")
    actions["duplicates_removed"] += int(duplicate.sum())
    invalid = (
        weather["weather_id"].isna()
        | weather["weather_date"].isna()
        | ~weather["farm_code"].isin(farm_codes)
        | weather[list(weather_numeric)].isna().any(axis=1)
        | ~weather["temperature_avg_c"].between(-10, 60)
        | ~weather["humidity_avg_pct"].between(0, 100)
        | ~weather["rainfall_mm"].between(0, 1_000)
        | ~weather["wind_speed_kph"].between(0, 250)
    )
    quarantine["weather_daily"] = _combine(
        [
            _reject(weather, duplicate, "duplicate_primary_key"),
            _reject(weather, invalid & ~duplicate, "invalid_fact_value_or_reference"),
        ],
        list(raw["weather_daily"].columns),
    )
    weather = weather.loc[~duplicate & ~invalid].copy()
    weather["weather_date"] = weather["weather_date"].dt.strftime("%Y-%m-%d")
    weather = weather.reset_index(drop=True)

    pest_types = raw["pest_types"].copy()
    original = pest_types["pest_code"].astype("string")
    pest_types["pest_code"] = _code(pest_types["pest_code"])
    actions["codes_canonicalized"] += int((original != pest_types["pest_code"]).sum())
    duplicate = pest_types.duplicated("pest_code", keep="first")
    invalid = pest_types[["pest_code", "pest_name", "pest_category"]].isna().any(axis=1)
    quarantine["pest_types"] = _combine(
        [
            _reject(pest_types, duplicate, "duplicate_primary_key"),
            _reject(pest_types, invalid & ~duplicate, "invalid_dimension_value"),
        ],
        list(raw["pest_types"].columns),
    )
    actions["duplicates_removed"] += int(duplicate.sum())
    pest_types = pest_types.loc[~duplicate & ~invalid].reset_index(drop=True)

    observations = raw["crop_health_observations"].copy()
    for column in (
        "observation_id",
        "farm_code",
        "field_code",
        "season_code",
        "pest_code",
    ):
        original = observations[column].astype("string")
        observations[column] = _code(observations[column])
        actions["codes_canonicalized"] += int((original != observations[column]).sum())
    observations["observed_at"] = pd.to_datetime(observations["observed_at"], errors="coerce")
    observations["affected_area_pct"] = pd.to_numeric(
        observations["affected_area_pct"], errors="coerce"
    )
    observations["plant_mortality_pct"] = pd.to_numeric(
        observations["plant_mortality_pct"], errors="coerce"
    )
    observations["severity"] = observations["severity"].astype("string").str.strip().str.lower()
    duplicate = observations.duplicated("observation_id", keep="first")
    actions["duplicates_removed"] += int(duplicate.sum())
    season_field_lookup = seasons.set_index("season_code")["field_code"].to_dict()
    valid_observation_relation = observations.apply(
        lambda row: (
            season_field_lookup.get(row["season_code"]) == row["field_code"]
            and field_farm_lookup.get(row["field_code"]) == row["farm_code"]
        ),
        axis=1,
    )
    invalid = (
        observations["observation_id"].isna()
        | observations["observed_at"].isna()
        | ~valid_observation_relation
        | ~observations["pest_code"].isin(pest_types["pest_code"])
        | ~observations["severity"].isin(("none", "low", "medium", "high"))
        | observations[["affected_area_pct", "plant_mortality_pct"]].isna().any(axis=1)
        | ~observations["affected_area_pct"].between(0, 100)
        | ~observations["plant_mortality_pct"].between(0, 100)
    )
    quarantine["crop_health_observations"] = _combine(
        [
            _reject(observations, duplicate, "duplicate_primary_key"),
            _reject(observations, invalid & ~duplicate, "invalid_fact_value_or_reference"),
        ],
        list(raw["crop_health_observations"].columns),
    )
    observations = observations.loc[~duplicate & ~invalid].copy()
    observations["observed_at"] = observations["observed_at"].dt.strftime(
        "%Y-%m-%dT%H:%M:%S"
    )
    observations = observations.reset_index(drop=True)

    return DomainTransformResult(
        silver={
            "sensors": sensors,
            "sensor_readings": readings,
            "weather_daily": weather,
            "pest_types": pest_types,
            "crop_health_observations": observations,
        },
        quarantine=quarantine,
        actions=actions,
    )

