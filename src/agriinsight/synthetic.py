from __future__ import annotations

import random
from datetime import datetime, time, timedelta

import pandas as pd

from agriinsight.config import GenerationConfig
from agriinsight.synthetic_crop_health import generate_crop_health
from agriinsight.synthetic_inventory import generate_inventory


FARM_CATALOG = (
    ("Nông trại Cao Nguyên", "Đắk Lắk", 12.6800, 108.0500),
    ("Nông trại Phù Sa", "An Giang", 10.5216, 105.1259),
    ("Nông trại Miền Đông", "Đồng Nai", 11.0686, 107.1676),
    ("Nông trại Biển Hồ", "Gia Lai", 13.9833, 108.0000),
    ("Nông trại Cửu Long", "Cần Thơ", 10.0452, 105.7469),
    ("Nông trại Bình Minh", "Lâm Đồng", 11.5753, 108.1429),
    ("Nông trại Hậu Giang", "Hậu Giang", 9.7579, 105.6413),
    ("Nông trại Tây Ninh", "Tây Ninh", 11.3352, 106.1099),
    ("Nông trại Đồng Tháp", "Đồng Tháp", 10.4938, 105.6882),
    ("Nông trại Sông Bé", "Bình Phước", 11.7512, 106.7235),
)

CROP_CATALOG = (
    {
        "crop_code": "COFFEE",
        "crop_name": "Cà phê",
        "category": "Cây công nghiệp",
        "duration_days": 280,
        "yield_kg_ha": 3_200,
        "price_vnd_kg": 72_000,
        "cost_vnd_ha": 115_000_000,
    },
    {
        "crop_code": "DURIAN",
        "crop_name": "Sầu riêng",
        "category": "Cây ăn trái",
        "duration_days": 250,
        "yield_kg_ha": 15_000,
        "price_vnd_kg": 82_000,
        "cost_vnd_ha": 390_000_000,
    },
    {
        "crop_code": "RICE",
        "crop_name": "Lúa",
        "category": "Cây lương thực",
        "duration_days": 115,
        "yield_kg_ha": 6_800,
        "price_vnd_kg": 8_800,
        "cost_vnd_ha": 31_000_000,
    },
    {
        "crop_code": "DRAGON_FRUIT",
        "crop_name": "Thanh long",
        "category": "Cây ăn trái",
        "duration_days": 190,
        "yield_kg_ha": 22_000,
        "price_vnd_kg": 18_500,
        "cost_vnd_ha": 175_000_000,
    },
    {
        "crop_code": "VEGETABLE",
        "crop_name": "Rau màu",
        "category": "Rau",
        "duration_days": 85,
        "yield_kg_ha": 19_000,
        "price_vnd_kg": 14_000,
        "cost_vnd_ha": 92_000_000,
    },
)

ACTIVITY_TYPES = (
    "Gieo trồng",
    "Tưới nước",
    "Bón phân",
    "Làm cỏ",
    "Kiểm tra sâu bệnh",
    "Phun thuốc",
    "Bảo dưỡng",
)

ACTIVITY_COST_WEIGHTS = {
    "Gieo trồng": 1.35,
    "Tưới nước": 1.05,
    "Bón phân": 1.65,
    "Làm cỏ": 0.80,
    "Kiểm tra sâu bệnh": 0.45,
    "Phun thuốc": 1.10,
    "Bảo dưỡng": 0.70,
}

NON_LABOR_COST_SHARE = {
    "Gieo trồng": 0.55,
    "Tưới nước": 0.38,
    "Bón phân": 0.76,
    "Làm cỏ": 0.12,
    "Kiểm tra sâu bệnh": 0.08,
    "Phun thuốc": 0.67,
    "Bảo dưỡng": 0.48,
}


def _round_money(value: float) -> int:
    return int(round(value / 1_000) * 1_000)


def generate_bronze(config: GenerationConfig) -> dict[str, pd.DataFrame]:
    """Generate deterministic, related operational datasets with known defects."""

    rng = random.Random(config.seed)

    farms: list[dict[str, object]] = []
    fields: list[dict[str, object]] = []
    seasons: list[dict[str, object]] = []
    activities: list[dict[str, object]] = []
    harvests: list[dict[str, object]] = []

    for farm_index in range(config.farm_count):
        farm_code = f"FARM-{farm_index + 1:03d}"
        farm_name, province, latitude, longitude = FARM_CATALOG[farm_index]
        farm_area = round(rng.uniform(90, 260), 2)
        farms.append(
            {
                "farm_code": farm_code,
                "farm_name": farm_name,
                "province": province,
                "registered_area_ha": farm_area,
                "latitude": latitude,
                "longitude": longitude,
            }
        )

        average_field_area = farm_area / (config.fields_per_farm + 1)
        for field_index in range(config.fields_per_farm):
            field_number = farm_index * config.fields_per_farm + field_index + 1
            field_code = f"FIELD-{field_number:04d}"
            crop = CROP_CATALOG[(farm_index + field_index) % len(CROP_CATALOG)]
            field_area = round(average_field_area * rng.uniform(0.72, 1.18), 2)
            fields.append(
                {
                    "field_code": field_code,
                    "farm_code": farm_code,
                    "field_name": f"Khu vực {farm_index + 1}.{field_index + 1}",
                    "area_ha": field_area,
                    "soil_type": rng.choice(("Đất đỏ bazan", "Đất phù sa", "Đất thịt", "Đất cát pha")),
                    "irrigation_type": rng.choice(("Nhỏ giọt", "Phun mưa", "Kênh dẫn")),
                    "latitude": round(latitude + rng.uniform(-0.08, 0.08), 6),
                    "longitude": round(longitude + rng.uniform(-0.08, 0.08), 6),
                }
            )

            for year in (2025, 2026):
                season_code = f"SEASON-{year}-{field_number:04d}"
                start_month = 1 + ((field_index + farm_index) % 3)
                start_day = 3 + ((field_number * 5) % 20)
                start_date = datetime(year, start_month, start_day).date()
                expected_harvest_date = start_date + timedelta(days=crop["duration_days"])
                status = "completed" if expected_harvest_date <= config.as_of_date else "active"
                target_yield = round(
                    field_area * float(crop["yield_kg_ha"]) * rng.uniform(0.94, 1.08),
                    2,
                )
                budget_cost = _round_money(
                    field_area * float(crop["cost_vnd_ha"]) * rng.uniform(0.94, 1.08)
                )
                seasons.append(
                    {
                        "season_code": season_code,
                        "field_code": field_code,
                        "crop_code": crop["crop_code"],
                        "start_date": start_date.isoformat(),
                        "expected_harvest_date": expected_harvest_date.isoformat(),
                        "target_yield_kg": target_yield,
                        "budget_cost_vnd": budget_cost,
                        "status": status,
                    }
                )

                activity_end = min(expected_harvest_date, config.as_of_date)
                active_days = max((activity_end - start_date).days, 1)
                season_progress = min(1.0, active_days / int(crop["duration_days"]))
                cost_factor = rng.uniform(0.90, 0.99)
                if (field_number + year) % 11 == 0:
                    cost_factor = rng.uniform(1.08, 1.16)
                season_actual_cost = budget_cost * season_progress * cost_factor
                activity_sequence = [
                    ACTIVITY_TYPES[index % len(ACTIVITY_TYPES)]
                    for index in range(config.activities_per_season)
                ]
                total_cost_weight = sum(
                    ACTIVITY_COST_WEIGHTS[activity_type]
                    for activity_type in activity_sequence
                )
                for activity_index in range(config.activities_per_season):
                    fraction = (activity_index + 1) / (config.activities_per_season + 1)
                    occurred_date = start_date + timedelta(days=int(active_days * fraction))
                    occurred_at = datetime.combine(
                        occurred_date,
                        time(hour=6 + (activity_index % 10), minute=(activity_index * 7) % 60),
                    )
                    activity_type = activity_sequence[activity_index]
                    has_material = activity_type in {"Gieo trồng", "Bón phân", "Phun thuốc"}
                    material_name = {
                        "Gieo trồng": "Hạt giống",
                        "Tưới nước": "Nước và điện tưới",
                        "Bón phân": "Phân NPK 16-16-8",
                        "Làm cỏ": "Dụng cụ làm cỏ",
                        "Kiểm tra sâu bệnh": "Dụng cụ kiểm tra",
                        "Phun thuốc": "Chế phẩm sinh học",
                        "Bảo dưỡng": "Nhiên liệu và phụ tùng",
                    }[activity_type]
                    quantity_kg = (
                        round(field_area * rng.uniform(4.0, 16.0), 3) if has_material else 0.0
                    )
                    activity_cost = _round_money(
                        season_actual_cost
                        * ACTIVITY_COST_WEIGHTS[activity_type]
                        / total_cost_weight
                        * rng.uniform(0.96, 1.04)
                    )
                    material_cost = _round_money(
                        activity_cost * NON_LABOR_COST_SHARE[activity_type]
                    )
                    labor_cost = max(activity_cost - material_cost, 0)
                    labor_hours = round(labor_cost / rng.uniform(52_000, 72_000), 2)
                    activity_id = f"ACT-{year}-{field_number:04d}-{activity_index + 1:03d}"
                    activities.append(
                        {
                            "activity_id": activity_id,
                            "occurred_at": occurred_at.isoformat(),
                            "farm_code": farm_code,
                            "field_code": field_code,
                            "season_code": season_code,
                            "activity_type": activity_type,
                            "material_name": material_name,
                            "quantity": quantity_kg,
                            "unit": "kg",
                            "labor_hours": labor_hours,
                            "material_cost_vnd": material_cost,
                            "labor_cost_vnd": labor_cost,
                            "notes": "Dữ liệu mô phỏng có seed",
                        }
                    )

                if status == "completed":
                    actual_ratio = rng.uniform(0.78, 1.13)
                    harvest_quantity = round(target_yield * actual_ratio, 2)
                    waste_quantity = round(harvest_quantity * rng.uniform(0.012, 0.065), 2)
                    revenue = _round_money(
                        (harvest_quantity - waste_quantity)
                        * float(crop["price_vnd_kg"])
                        * rng.uniform(0.93, 1.07)
                    )
                    harvests.append(
                        {
                            "harvest_id": f"HARVEST-{year}-{field_number:04d}",
                            "harvested_at": datetime.combine(
                                expected_harvest_date, time(hour=8)
                            ).isoformat(),
                            "farm_code": farm_code,
                            "field_code": field_code,
                            "season_code": season_code,
                            "crop_code": crop["crop_code"],
                            "quantity": harvest_quantity,
                            "unit": "kg",
                            "waste_quantity_kg": waste_quantity,
                            "quality_grade": rng.choice(("A", "A", "B", "B", "C")),
                            "revenue_vnd": revenue,
                        }
                    )

    crops = [
        {key: crop[key] for key in ("crop_code", "crop_name", "category")}
        for crop in CROP_CATALOG
    ]

    activity_df = pd.DataFrame(activities)
    harvest_df = pd.DataFrame(harvests)

    # Known source-system defects: aliases, mixed units, duplicate IDs and impossible values.
    if not activity_df.empty:
        activity_df.loc[0, "farm_code"] = f" {str(activity_df.loc[0, 'farm_code']).lower()} "
        tonne_rows = activity_df.index[(activity_df.index % 17 == 0) & (activity_df["quantity"] > 0)]
        activity_df.loc[tonne_rows, "quantity"] = activity_df.loc[tonne_rows, "quantity"] / 1_000
        activity_df.loc[tonne_rows, "unit"] = "tonne"
        activity_df = pd.concat([activity_df, activity_df.iloc[[0]]], ignore_index=True)
        invalid_activity = activity_df.iloc[[1]].copy()
        invalid_activity.loc[:, "activity_id"] = "ACT-INVALID-NEGATIVE-COST"
        invalid_activity.loc[:, "material_cost_vnd"] = -5_000_000
        activity_df = pd.concat([activity_df, invalid_activity], ignore_index=True)

    if not harvest_df.empty:
        harvest_df.loc[0, "season_code"] = f" {str(harvest_df.loc[0, 'season_code']).lower()} "
        harvest_df.loc[0, "quantity"] = float(harvest_df.loc[0, "quantity"]) / 1_000
        harvest_df.loc[0, "unit"] = "tonne"
        harvest_df = pd.concat([harvest_df, harvest_df.iloc[[0]]], ignore_index=True)
        invalid_harvest = harvest_df.iloc[[0]].copy()
        invalid_harvest.loc[:, "harvest_id"] = "HARVEST-INVALID-NEGATIVE-REVENUE"
        invalid_harvest.loc[:, "revenue_vnd"] = -10_000_000
        harvest_df = pd.concat([harvest_df, invalid_harvest], ignore_index=True)

    core = {
        "farms": pd.DataFrame(farms),
        "fields": pd.DataFrame(fields),
        "crops": pd.DataFrame(crops),
        "seasons": pd.DataFrame(seasons),
        "activities": activity_df,
        "harvests": harvest_df,
    }
    inventory = generate_inventory(config, core["farms"])
    crop_health = generate_crop_health(
        config,
        core["farms"],
        core["fields"],
        core["seasons"],
    )
    return {**core, **inventory, **crop_health}
