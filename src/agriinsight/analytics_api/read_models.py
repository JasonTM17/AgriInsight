from __future__ import annotations

from typing import Any

import pandas as pd

from agriinsight.analytics_api.auth_scope import AuthorizedScope
from agriinsight.analytics_api.filter_scope import AppliedAnalyticsFilter
from agriinsight.analytics_api.filtered_read_models import (
    filtered_farms_payload,
    filtered_overview_payload,
)
from agriinsight.analytics_api.models import (
    CatalogPayload,
    FarmsPayload,
    OverviewPayload,
    PageModel,
)
from agriinsight.analytics_api.response_shaping import first_record, json_safe, records
from agriinsight.analytics_snapshot import ArtifactSnapshot


def catalog_payload(
    farms: list[Any],
    warehouses: list[Any],
) -> CatalogPayload:
    return CatalogPayload(
        allowed_farms=[
            {
                "code": item.code,
                "displayName": item.displayName,
                "id": str(item.id),
            }
            for item in farms
            if item.active
        ],
        allowed_warehouses=[
            {
                "code": item.code,
                "displayName": item.displayName,
                "id": str(item.id),
                "locationText": item.locationText,
            }
            for item in warehouses
            if item.active
        ],
    )


def overview_payload(
    snapshot: ArtifactSnapshot,
    scope: AuthorizedScope,
    applied_filter: AppliedAnalyticsFilter | None = None,
) -> tuple[OverviewPayload, bool, bool]:
    if applied_filter is not None and applied_filter.is_filtered:
        payload, missing = filtered_overview_payload(
            snapshot,
            scope,
            applied_filter,
        )
        return payload, False, missing
    if scope.farm_tenant_wide:
        insight_document = snapshot.json["insights"]
        return (
            OverviewPayload(
                insights=json_safe(insight_document.get("insights", [])),
                monthly_trend=records(snapshot.csv["monthly_financials"]),
                summary=first_record(snapshot.csv["executive_summary"]),
                top_risks=records(snapshot.csv["risk_alerts"].head(25)),
            ),
            False,
            snapshot.csv["executive_summary"].empty,
        )

    farms = _farm_rows(snapshot, scope)
    summary = _farm_summary(farms)
    return (
        OverviewPayload(
            insights=[],
            monthly_trend=[],
            summary=json_safe(summary),
            top_risks=[],
        ),
        True,
        farms.empty,
    )


def farms_payload(
    snapshot: ArtifactSnapshot,
    scope: AuthorizedScope,
    *,
    farm_code: str | None,
    limit: int,
    offset: int,
    sort: str,
    applied_filter: AppliedAnalyticsFilter | None = None,
) -> FarmsPayload:
    if (
        applied_filter is not None
        and applied_filter.requires_event_aggregation
    ):
        return filtered_farms_payload(
            snapshot,
            scope,
            applied_filter,
            limit=limit,
            offset=offset,
            sort=sort,
        )
    farms = _farm_rows(snapshot, scope)
    if farm_code:
        farms = farms[farms["farm_code"] == farm_code]
    ascending = sort != "profit_desc"
    sort_column = "farm_code" if sort == "farm_code" else "profit_vnd"
    farms = farms.sort_values(sort_column, ascending=ascending, kind="stable")
    page = farms.iloc[offset : offset + limit]
    if scope.farm_tenant_wide and farm_code is None:
        crop_source = snapshot.csv["crop_profitability"]
    else:
        selected_farms = (
            frozenset({farm_code}) if farm_code is not None else scope.farm_codes
        )
        crop_source = _scoped_crop_profitability(
            snapshot.csv["cost_season"], selected_farms
        )
    return FarmsPayload(
        crop_profitability=records(crop_source),
        items=records(page),
        page=PageModel(
            has_more=offset + limit < len(farms),
            limit=limit,
            offset=offset,
            total=len(farms),
        ),
    )


def _farm_rows(
    snapshot: ArtifactSnapshot,
    scope: AuthorizedScope,
) -> pd.DataFrame:
    farms = snapshot.csv["farm_performance"]
    return farms[farms["farm_code"].isin(scope.farm_codes)].copy()


def _farm_summary(farms: pd.DataFrame) -> dict[str, Any]:
    revenue = float(farms["total_revenue_vnd"].sum())
    cost = float(farms["total_cost_vnd"].sum())
    profit = revenue - cost
    return {
        "total_revenue_vnd": revenue,
        "total_cost_vnd": cost,
        "profit_vnd": profit,
        "profit_margin_pct": (profit / revenue * 100) if revenue else 0.0,
        "harvest_quantity_kg": float(farms["harvest_quantity_kg"].sum()),
        "cultivated_area_ha": float(farms["cultivated_area_ha"].sum()),
        "farm_count": int(len(farms)),
    }


def _scoped_crop_profitability(
    seasons: pd.DataFrame,
    farm_codes: frozenset[str],
) -> pd.DataFrame:
    scoped = seasons[seasons["farm_code"].isin(farm_codes)]
    if scoped.empty:
        return pd.DataFrame()
    grouped = (
        scoped.groupby(["crop_code", "crop_name"], as_index=False)
        .agg(
            operated_area_ha=("area_ha", "sum"),
            harvest_quantity_kg=("harvest_quantity_kg", "sum"),
            total_revenue_vnd=("revenue_vnd", "sum"),
            total_cost_vnd=("operating_total_cost_vnd", "sum"),
            profit_vnd=("operating_profit_vnd", "sum"),
        )
        .sort_values("profit_vnd", ascending=False, kind="stable")
    )
    grouped["profit_margin_pct"] = grouped.apply(
        lambda row: (
            row["profit_vnd"] / row["total_revenue_vnd"] * 100
            if row["total_revenue_vnd"]
            else 0.0
        ),
        axis=1,
    )
    return grouped
