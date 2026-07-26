from __future__ import annotations

import pandas as pd

from agriinsight.analytics_api.auth_scope import AuthorizedScope
from agriinsight.analytics_api.filter_scope import (
    AppliedAnalyticsFilter,
    selected_relationships,
)
from agriinsight.analytics_api.record_models import (
    CropProfitabilityModel,
    FarmPerformanceModel,
    FarmScopeSummaryModel,
    MonthlyFinancialModel,
)
from agriinsight.analytics_api.response_shaping import records
from agriinsight.analytics_snapshot import ArtifactSnapshot


def selected_fact_frames(
    snapshot: ArtifactSnapshot,
    scope: AuthorizedScope,
    applied: AppliedAnalyticsFilter,
) -> tuple[pd.DataFrame, pd.DataFrame, pd.DataFrame]:
    relations = selected_relationships(snapshot, scope, applied)
    seasons = frozenset(relations["season_code"].astype(str))
    activities = snapshot.csv["cost_activity_detail"]
    activities = activities[activities["season_code"].isin(seasons)].copy()
    harvests = snapshot.csv["harvests"]
    harvests = harvests[harvests["season_code"].isin(seasons)].copy()
    return (
        relations,
        _date_window(activities, "occurred_at", applied),
        _date_window(harvests, "harvested_at", applied),
    )


def summary(
    relations: pd.DataFrame,
    activities: pd.DataFrame,
    harvests: pd.DataFrame,
) -> FarmScopeSummaryModel:
    revenue = float(harvests["revenue_vnd"].sum())
    cost = float(activities["operating_total_cost_vnd"].sum())
    profit = revenue - cost
    return FarmScopeSummaryModel(
        cultivated_area_ha=_cultivated_area(relations),
        farm_count=int(relations["farm_code"].nunique()),
        harvest_quantity_kg=float(harvests["harvest_quantity_kg"].sum()),
        profit_margin_pct=(profit / revenue * 100) if revenue else 0.0,
        profit_vnd=profit,
        total_cost_vnd=cost,
        total_revenue_vnd=revenue,
    )


def monthly_trend(
    activities: pd.DataFrame,
    harvests: pd.DataFrame,
) -> list[MonthlyFinancialModel]:
    costs = _monthly_sum(
        activities,
        "occurred_at",
        "operating_total_cost_vnd",
        "cost_vnd",
    )
    revenue = _monthly_sum(
        harvests,
        "harvested_at",
        "revenue_vnd",
        "revenue_vnd",
    )
    monthly = costs.merge(revenue, on="month", how="outer").fillna(0.0)
    if monthly.empty:
        return []
    monthly["profit_vnd"] = monthly["revenue_vnd"] - monthly["cost_vnd"]
    monthly = monthly.sort_values("month", kind="stable")
    return [
        MonthlyFinancialModel.model_validate(item)
        for item in records(
            monthly[["month", "revenue_vnd", "cost_vnd", "profit_vnd"]]
        )
    ]


def farm_performance(
    relations: pd.DataFrame,
    activities: pd.DataFrame,
    harvests: pd.DataFrame,
) -> list[FarmPerformanceModel]:
    event_farms = set(activities["farm_code"]) | set(harvests["farm_code"])
    items: list[FarmPerformanceModel] = []
    for farm_code in sorted(event_farms):
        farm_relations = relations[relations["farm_code"] == farm_code]
        farm_activities = activities[activities["farm_code"] == farm_code]
        farm_harvests = harvests[harvests["farm_code"] == farm_code]
        cultivated_area = _cultivated_area(farm_relations)
        operated_area = float(farm_relations["area_ha"].sum())
        harvested_seasons = frozenset(farm_harvests["season_code"])
        harvested_area = float(
            farm_relations[
                farm_relations["season_code"].isin(harvested_seasons)
            ]["area_ha"].sum()
        )
        revenue = float(farm_harvests["revenue_vnd"].sum())
        cost = float(farm_activities["operating_total_cost_vnd"].sum())
        profit = revenue - cost
        quantity = float(farm_harvests["harvest_quantity_kg"].sum())
        items.append(
            FarmPerformanceModel(
                cost_vnd_per_ha=cost / operated_area if operated_area else 0.0,
                cultivated_area_ha=cultivated_area,
                farm_code=str(farm_code),
                farm_name=str(farm_relations.iloc[0]["farm_name"]),
                harvest_quantity_kg=quantity,
                harvested_area_ha=harvested_area,
                profit_margin_pct=(profit / revenue * 100) if revenue else 0.0,
                profit_vnd=profit,
                total_cost_vnd=cost,
                total_revenue_vnd=revenue,
                yield_kg_per_ha=quantity / harvested_area if harvested_area else 0.0,
            )
        )
    return items


def crop_profitability(
    relations: pd.DataFrame,
    activities: pd.DataFrame,
    harvests: pd.DataFrame,
) -> list[CropProfitabilityModel]:
    event_crops = set(activities["crop_code"]) | set(harvests["crop_code"])
    items: list[CropProfitabilityModel] = []
    for crop_code in sorted(event_crops):
        crop_relations = relations[relations["crop_code"] == crop_code]
        crop_activities = activities[activities["crop_code"] == crop_code]
        crop_harvests = harvests[harvests["crop_code"] == crop_code]
        revenue = float(crop_harvests["revenue_vnd"].sum())
        cost = float(crop_activities["operating_total_cost_vnd"].sum())
        profit = revenue - cost
        items.append(
            CropProfitabilityModel(
                crop_code=str(crop_code),
                crop_name=str(crop_relations.iloc[0]["crop_name"]),
                harvest_quantity_kg=float(
                    crop_harvests["harvest_quantity_kg"].sum()
                ),
                operated_area_ha=float(crop_relations["area_ha"].sum()),
                profit_margin_pct=(profit / revenue * 100) if revenue else 0.0,
                profit_vnd=profit,
                total_cost_vnd=cost,
                total_revenue_vnd=revenue,
            )
        )
    return items


def _cultivated_area(relations: pd.DataFrame) -> float:
    if relations.empty:
        return 0.0
    return float(
        relations.groupby("field_code", as_index=False)["area_ha"]
        .max()["area_ha"]
        .sum()
    )


def _date_window(
    frame: pd.DataFrame,
    timestamp_column: str,
    applied: AppliedAnalyticsFilter,
) -> pd.DataFrame:
    timestamps = pd.to_datetime(frame[timestamp_column], errors="raise").dt.date
    selected = frame[timestamps <= applied.date_to]
    if applied.date_from is not None:
        selected = selected[timestamps >= applied.date_from]
    return selected.copy()


def _monthly_sum(
    frame: pd.DataFrame,
    timestamp_column: str,
    value_column: str,
    output_column: str,
) -> pd.DataFrame:
    if frame.empty:
        return pd.DataFrame(columns=["month", output_column])
    values = frame[[timestamp_column, value_column]].copy()
    values["month"] = pd.to_datetime(
        values[timestamp_column],
        errors="raise",
    ).dt.to_period("M").astype(str)
    return (
        values.groupby("month", as_index=False)[value_column]
        .sum()
        .rename(columns={value_column: output_column})
    )
