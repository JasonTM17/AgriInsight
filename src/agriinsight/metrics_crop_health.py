from __future__ import annotations

import sqlite3
from datetime import date

import pandas as pd


def _risk_score(row: pd.Series) -> int:
    score = 0.0
    moisture = row.get("soil_moisture_pct")
    temperature = row.get("temperature_c")
    soil_ph = row.get("soil_ph")
    rainfall = row.get("rainfall_7d_mm")
    sensor_age = row.get("sensor_age_days")
    if pd.isna(moisture) or moisture < 25 or moisture > 85:
        score += 35
    elif moisture < 35 or moisture > 75:
        score += 20
    if pd.notna(temperature) and (temperature > 35 or temperature < 12):
        score += 20
    if pd.notna(soil_ph) and not 5.0 <= soil_ph <= 7.5:
        score += 20
    if pd.notna(rainfall) and rainfall < 5:
        score += 15
    elif pd.notna(rainfall) and rainfall > 120:
        score += 10
    score += min(float(row.get("max_affected_area_pct", 0) or 0) * 1.4, 35)
    score += min(float(row.get("max_mortality_pct", 0) or 0) * 2, 20)
    if pd.isna(sensor_age) or sensor_age > 2:
        score += 40
    return min(100, int(round(score)))


def _recommendation(row: pd.Series) -> str:
    if pd.isna(row["sensor_age_days"]) or row["sensor_age_days"] > 2:
        return "Kiểm tra cảm biến và kết nối IoT trong 4 giờ."
    if row["pest_cases_90d"] > 0 and row["max_affected_area_pct"] >= 10:
        return "Kiểm tra thực địa trong 24 giờ và lập phương án kiểm soát dịch hại."
    if row["soil_moisture_pct"] < 35:
        return "Kiểm tra hệ thống tưới và tăng lịch tưới có kiểm soát."
    if row["soil_moisture_pct"] > 75:
        return "Giảm tưới, kiểm tra thoát nước và nguy cơ bệnh nấm."
    if not 5.0 <= row["soil_ph"] <= 7.5:
        return "Lấy mẫu đất xác nhận pH trước khi điều chỉnh dinh dưỡng."
    return "Tiếp tục theo dõi theo lịch vận hành hiện tại."


def build_crop_health_gold(
    connection: sqlite3.Connection, as_of_date: date
) -> dict[str, pd.DataFrame]:
    as_of = as_of_date.isoformat()
    field_status = pd.read_sql_query(
        """
        WITH reading_7d AS (
            SELECT field_key,
                   AVG(temperature_c) AS temperature_c,
                   AVG(air_humidity_pct) AS air_humidity_pct,
                   AVG(soil_moisture_pct) AS soil_moisture_pct,
                   AVG(soil_ph) AS soil_ph,
                   AVG(battery_pct) AS battery_pct,
                   COUNT(*) AS reading_count_7d
            FROM fact_sensor_reading
            WHERE date(observed_at) >= date(?, '-6 day')
            GROUP BY field_key
        ),
        latest_sensor AS (
            SELECT field_key, MAX(observed_at) AS last_reading_at
            FROM fact_sensor_reading
            GROUP BY field_key
        ),
        health_90d AS (
            SELECT h.field_key,
                   SUM(CASE WHEN p.pest_code <> 'NONE' THEN 1 ELSE 0 END) AS pest_cases_90d,
                   MAX(h.affected_area_pct) AS max_affected_area_pct,
                   MAX(h.plant_mortality_pct) AS max_mortality_pct
            FROM fact_crop_health_observation h
            JOIN dim_pest_type p USING (pest_key)
            WHERE date(h.observed_at) >= date(?, '-89 day')
            GROUP BY h.field_key
        ),
        rainfall_7d AS (
            SELECT farm_key, SUM(rainfall_mm) AS rainfall_7d_mm
            FROM fact_weather_daily
            WHERE date(weather_date) >= date(?, '-6 day')
            GROUP BY farm_key
        ),
        ranked_season AS (
            SELECT season_key,
                   field_key,
                   crop_key,
                   ROW_NUMBER() OVER (
                       PARTITION BY field_key
                       ORDER BY CASE WHEN status = 'active' THEN 0 ELSE 1 END, start_date DESC
                   ) AS rank_number
            FROM dim_season
        )
        SELECT f.farm_code,
               f.farm_name,
               fi.field_code,
               fi.field_name,
               fi.area_ha,
               fi.latitude,
               fi.longitude,
               c.crop_code,
               c.crop_name,
               r.temperature_c,
               r.air_humidity_pct,
               r.soil_moisture_pct,
               r.soil_ph,
               r.battery_pct,
               COALESCE(r.reading_count_7d, 0) AS reading_count_7d,
               ls.last_reading_at,
               CAST(julianday(?) - julianday(substr(ls.last_reading_at, 1, 10)) AS INTEGER)
                   AS sensor_age_days,
               COALESCE(h.pest_cases_90d, 0) AS pest_cases_90d,
               COALESCE(h.max_affected_area_pct, 0) AS max_affected_area_pct,
               COALESCE(h.max_mortality_pct, 0) AS max_mortality_pct,
               COALESCE(w.rainfall_7d_mm, 0) AS rainfall_7d_mm
        FROM dim_field fi
        JOIN dim_farm f USING (farm_key)
        LEFT JOIN ranked_season rs ON rs.field_key = fi.field_key AND rs.rank_number = 1
        LEFT JOIN dim_crop c ON c.crop_key = rs.crop_key
        LEFT JOIN reading_7d r USING (field_key)
        LEFT JOIN latest_sensor ls USING (field_key)
        LEFT JOIN health_90d h USING (field_key)
        LEFT JOIN rainfall_7d w ON w.farm_key = fi.farm_key
        ORDER BY f.farm_name, fi.field_name
        """,
        connection,
        params=(as_of, as_of, as_of, as_of),
    )
    field_status["risk_score"] = field_status.apply(_risk_score, axis=1)
    field_status["risk_status"] = field_status["risk_score"].map(
        lambda score: "high" if score >= 50 else "watch" if score >= 25 else "healthy"
    )
    field_status["recommended_action"] = field_status.apply(_recommendation, axis=1)
    field_status = field_status.sort_values(
        ["risk_score", "farm_name", "field_name"], ascending=[False, True, True]
    ).reset_index(drop=True)

    environment_daily = pd.read_sql_query(
        """
        SELECT d.full_date AS reading_date,
               f.farm_code,
               f.farm_name,
               fi.field_code,
               fi.field_name,
               AVG(r.temperature_c) AS temperature_c,
               AVG(r.air_humidity_pct) AS air_humidity_pct,
               AVG(r.soil_moisture_pct) AS soil_moisture_pct,
               AVG(r.soil_ph) AS soil_ph,
               AVG(r.rainfall_mm) AS rainfall_mm
        FROM fact_sensor_reading r
        JOIN dim_date d USING (date_key)
        JOIN dim_farm f USING (farm_key)
        JOIN dim_field fi USING (field_key)
        WHERE date(d.full_date) >= date(?, '-59 day')
        GROUP BY d.full_date, f.farm_code, f.farm_name, fi.field_code, fi.field_name
        ORDER BY d.full_date, f.farm_name, fi.field_name
        """,
        connection,
        params=(as_of,),
    )

    pest_weekly = pd.read_sql_query(
        """
        SELECT strftime('%Y-W%W', h.observed_at) AS week,
               p.pest_code,
               p.pest_name,
               COUNT(*) AS case_count,
               AVG(h.affected_area_pct) AS average_affected_area_pct,
               MAX(h.affected_area_pct) AS max_affected_area_pct
        FROM fact_crop_health_observation h
        JOIN dim_pest_type p USING (pest_key)
        WHERE p.pest_code <> 'NONE'
        GROUP BY week, p.pest_code, p.pest_name
        ORDER BY week, p.pest_name
        """,
        connection,
    )

    alerts = field_status[
        (field_status["risk_status"] != "healthy")
        | (field_status["pest_cases_90d"] > 0)
        | (field_status["sensor_age_days"] > 2)
    ].copy()
    alert_columns = (
        "farm_code",
        "farm_name",
        "field_code",
        "field_name",
        "crop_name",
        "risk_score",
        "risk_status",
        "soil_moisture_pct",
        "soil_ph",
        "rainfall_7d_mm",
        "pest_cases_90d",
        "max_affected_area_pct",
        "sensor_age_days",
        "recommended_action",
    )
    alerts = alerts[list(alert_columns)].reset_index(drop=True)

    crop_health_summary = pd.DataFrame(
        [
            {
                "monitored_fields": len(field_status),
                "readings_7d": int(field_status["reading_count_7d"].sum()),
                "average_temperature_c": round(float(field_status["temperature_c"].mean()), 2),
                "average_soil_moisture_pct": round(
                    float(field_status["soil_moisture_pct"].mean()), 2
                ),
                "average_soil_ph": round(float(field_status["soil_ph"].mean()), 2),
                "high_risk_fields": int((field_status["risk_status"] == "high").sum()),
                "watch_fields": int((field_status["risk_status"] == "watch").sum()),
                "offline_sensors": int((field_status["sensor_age_days"] > 2).sum()),
                "pest_cases_90d": int(field_status["pest_cases_90d"].sum()),
            }
        ]
    )

    return {
        "crop_health_summary": crop_health_summary,
        "field_health_status": field_status,
        "environment_daily": environment_daily,
        "pest_incidents_weekly": pest_weekly,
        "crop_health_alerts": alerts,
    }
