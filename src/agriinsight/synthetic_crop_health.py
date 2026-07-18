from __future__ import annotations

import math
import random
from datetime import date, datetime, time, timedelta

import pandas as pd

from agriinsight.config import GenerationConfig


PEST_CATALOG = (
    ("NONE", "Không ghi nhận", "none"),
    ("FUNGUS", "Bệnh nấm", "disease"),
    ("APHID", "Rệp mềm", "insect"),
    ("BORER", "Sâu đục thân", "insect"),
    ("BLIGHT", "Bệnh cháy lá", "disease"),
)


def generate_crop_health(
    config: GenerationConfig,
    farms: pd.DataFrame,
    fields: pd.DataFrame,
    seasons: pd.DataFrame,
) -> dict[str, pd.DataFrame]:
    """Generate IoT readings, weather API data and field health observations."""

    rng = random.Random(config.seed + 202)
    sensors = pd.DataFrame(
        [
            {
                "sensor_code": f"SENSOR-{index + 1:04d}",
                "field_code": field.field_code,
                "sensor_model": "AgriSense Multi-6",
                "installed_at": date(2024, 12, 1).isoformat(),
                "status": "active",
            }
            for index, field in enumerate(fields.itertuples(index=False))
        ]
    )

    weather_days = max(config.sensor_history_days, 180)
    weather_start = config.as_of_date - timedelta(days=weather_days - 1)
    weather_rows: list[dict[str, object]] = []
    weather_lookup: dict[tuple[str, date], dict[str, float]] = {}
    weather_number = 0
    for farm_index, farm in enumerate(farms.itertuples(index=False)):
        for offset in range(weather_days):
            weather_date = weather_start + timedelta(days=offset)
            seasonal = math.sin(2 * math.pi * weather_date.timetuple().tm_yday / 365)
            temperature = 27.5 + seasonal * 2.8 + farm_index * 0.22 + rng.uniform(-1.8, 1.8)
            humidity = 72 - seasonal * 5 + rng.uniform(-7, 7)
            rainfall = max(0.0, rng.gauss(5.2 + max(seasonal, 0) * 5.5, 7.5))
            if rng.random() < 0.38:
                rainfall = 0.0
            weather_number += 1
            row = {
                "weather_id": f"WEATHER-{weather_number:07d}",
                "weather_date": weather_date.isoformat(),
                "farm_code": farm.farm_code,
                "temperature_avg_c": round(temperature, 2),
                "humidity_avg_pct": round(max(35, min(humidity, 98)), 2),
                "rainfall_mm": round(rainfall, 2),
                "wind_speed_kph": round(max(0.5, rng.gauss(9, 3.2)), 2),
                "source": "AgriInsight Weather Simulator",
            }
            weather_rows.append(row)
            weather_lookup[(farm.farm_code, weather_date)] = {
                "temperature": float(row["temperature_avg_c"]),
                "humidity": float(row["humidity_avg_pct"]),
                "rainfall": float(row["rainfall_mm"]),
            }

    field_farm = fields.set_index("field_code")["farm_code"].to_dict()
    sensor_start = config.as_of_date - timedelta(days=config.sensor_history_days - 1)
    reading_rows: list[dict[str, object]] = []
    reading_number = 0
    for sensor_index, sensor in enumerate(sensors.itertuples(index=False)):
        farm_code = field_farm[sensor.field_code]
        last_reading_date = (
            config.as_of_date - timedelta(days=5)
            if sensor_index % 13 == 0
            else config.as_of_date
        )
        field_moisture_bias = rng.uniform(-8, 8)
        field_ph = rng.uniform(5.3, 7.1)
        for day_offset in range(config.sensor_history_days):
            reading_date = sensor_start + timedelta(days=day_offset)
            if reading_date > last_reading_date:
                continue
            weather = weather_lookup[(farm_code, reading_date)]
            for reading_index in range(config.sensor_readings_per_day):
                hour = int(reading_index * 24 / config.sensor_readings_per_day)
                observed_at = datetime.combine(reading_date, time(hour=hour))
                daily_wave = math.sin(2 * math.pi * (hour - 8) / 24)
                temperature = weather["temperature"] + daily_wave * 4.2 + rng.uniform(-0.7, 0.7)
                humidity = weather["humidity"] - daily_wave * 10 + rng.uniform(-2.5, 2.5)
                soil_moisture = (
                    47
                    + field_moisture_bias
                    + min(weather["rainfall"] * 0.55, 20)
                    - daily_wave * 3
                    + rng.uniform(-4, 4)
                )
                reading_number += 1
                reading_rows.append(
                    {
                        "reading_id": f"READING-{reading_number:09d}",
                        "observed_at": observed_at.isoformat(),
                        "sensor_code": sensor.sensor_code,
                        "farm_code": farm_code,
                        "field_code": sensor.field_code,
                        "temperature_c": round(temperature, 2),
                        "air_humidity_pct": round(max(20, min(humidity, 100)), 2),
                        "soil_moisture_pct": round(max(8, min(soil_moisture, 98)), 2),
                        "soil_ph": round(field_ph + rng.uniform(-0.18, 0.18), 2),
                        "rainfall_mm": weather["rainfall"],
                        "battery_pct": round(
                            min(100, max(12, 100 - day_offset * 0.08 + rng.uniform(-2, 2))),
                            2,
                        ),
                    }
                )

    reading_frame = pd.DataFrame(reading_rows)
    if not reading_frame.empty:
        reading_frame.loc[0, "sensor_code"] = (
            f" {str(reading_frame.loc[0, 'sensor_code']).lower()} "
        )
        reading_frame = pd.concat([reading_frame, reading_frame.iloc[[0]]], ignore_index=True)
        invalid_humidity = reading_frame.iloc[[1]].copy()
        invalid_humidity.loc[:, "reading_id"] = "READING-INVALID-HUMIDITY"
        invalid_humidity.loc[:, "air_humidity_pct"] = 160
        missing_moisture = reading_frame.iloc[[2]].copy()
        missing_moisture.loc[:, "reading_id"] = "READING-MISSING-MOISTURE"
        reading_frame = pd.concat(
            [reading_frame, invalid_humidity, missing_moisture], ignore_index=True
        )
        reading_frame.loc[reading_frame.index[-1], "soil_moisture_pct"] = float("nan")

    weather_frame = pd.DataFrame(weather_rows)
    if not weather_frame.empty:
        weather_frame = pd.concat([weather_frame, weather_frame.iloc[[0]]], ignore_index=True)
        invalid_weather = weather_frame.iloc[[1]].copy()
        invalid_weather.loc[:, "weather_id"] = "WEATHER-INVALID-RAINFALL"
        invalid_weather.loc[:, "rainfall_mm"] = -25
        weather_frame = pd.concat([weather_frame, invalid_weather], ignore_index=True)

    field_lookup = fields.set_index("field_code")
    observation_rows: list[dict[str, object]] = []
    for index, season in enumerate(seasons.itertuples(index=False)):
        start_date = date.fromisoformat(season.start_date)
        if start_date > config.as_of_date:
            continue
        observed_date = min(
            config.as_of_date - timedelta(days=(index * 7) % 90),
            start_date + timedelta(days=75),
        )
        if observed_date < start_date:
            observed_date = start_date
        pest_code = PEST_CATALOG[(index * 3 + config.seed) % len(PEST_CATALOG)][0]
        if index % 5 in (0, 1, 2):
            pest_code = "NONE"
        if pest_code == "NONE":
            severity = "none"
            affected_area = 0.0
            mortality = round(rng.uniform(0, 0.5), 2)
        else:
            affected_area = round(rng.uniform(3, 28), 2)
            mortality = round(rng.uniform(0.3, 6.5), 2)
            severity = "high" if affected_area >= 20 else "medium" if affected_area >= 10 else "low"
        field = field_lookup.loc[season.field_code]
        observation_rows.append(
            {
                "observation_id": f"HEALTH-{index + 1:06d}",
                "observed_at": datetime.combine(observed_date, time(hour=9)).isoformat(),
                "farm_code": field["farm_code"],
                "field_code": season.field_code,
                "season_code": season.season_code,
                "pest_code": pest_code,
                "severity": severity,
                "affected_area_pct": affected_area,
                "plant_mortality_pct": mortality,
                "notes": "Quan sát hiện trường mô phỏng",
            }
        )

    observation_frame = pd.DataFrame(observation_rows)
    if not observation_frame.empty:
        observation_frame = pd.concat(
            [observation_frame, observation_frame.iloc[[0]]], ignore_index=True
        )
        invalid_observation = observation_frame.iloc[[1]].copy()
        invalid_observation.loc[:, "observation_id"] = "HEALTH-INVALID-AFFECTED-AREA"
        invalid_observation.loc[:, "affected_area_pct"] = 140
        observation_frame = pd.concat(
            [observation_frame, invalid_observation], ignore_index=True
        )

    pest_types = pd.DataFrame(
        [
            {"pest_code": code, "pest_name": name, "pest_category": category}
            for code, name, category in PEST_CATALOG
        ]
    )
    return {
        "sensors": sensors,
        "sensor_readings": reading_frame,
        "weather_daily": weather_frame,
        "pest_types": pest_types,
        "crop_health_observations": observation_frame,
    }
