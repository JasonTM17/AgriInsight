from __future__ import annotations

from agriinsight.analytics_api.auth_scope import AuthorizedScope
from agriinsight.analytics_api.filter_scope import AppliedAnalyticsFilter
from agriinsight.analytics_api.filtered_kpi_aggregation import (
    crop_profitability,
    farm_performance,
    monthly_trend,
    selected_fact_frames,
    summary,
)
from agriinsight.analytics_api.models import (
    FarmsPayload,
    OverviewPayload,
    PageModel,
)
from agriinsight.analytics_api.response_shaping import records
from agriinsight.analytics_snapshot import ArtifactSnapshot


def filtered_overview_payload(
    snapshot: ArtifactSnapshot,
    scope: AuthorizedScope,
    applied: AppliedAnalyticsFilter,
) -> tuple[OverviewPayload, bool]:
    relations, activities, harvests = selected_fact_frames(
        snapshot,
        scope,
        applied,
    )
    selected_seasons = frozenset(relations["season_code"].astype(str))
    risks = snapshot.csv["risk_alerts"]
    risks = risks[risks["season_code"].isin(selected_seasons)]
    return (
        OverviewPayload(
            insights=[],
            monthly_trend=monthly_trend(activities, harvests),
            summary=summary(relations, activities, harvests),
            top_risks=records(risks.head(25)),
        ),
        activities.empty and harvests.empty,
    )


def filtered_farms_payload(
    snapshot: ArtifactSnapshot,
    scope: AuthorizedScope,
    applied: AppliedAnalyticsFilter,
    *,
    limit: int,
    offset: int,
    sort: str,
) -> FarmsPayload:
    relations, activities, harvests = selected_fact_frames(
        snapshot,
        scope,
        applied,
    )
    farm_items = farm_performance(relations, activities, harvests)
    farm_items.sort(
        key=(
            (lambda item: (-item.profit_vnd, item.farm_code))
            if sort == "profit_desc"
            else (lambda item: item.farm_code)
        )
    )
    page_items = farm_items[offset : offset + limit]
    return FarmsPayload(
        crop_profitability=crop_profitability(relations, activities, harvests),
        items=page_items,
        page=PageModel(
            has_more=offset + limit < len(farm_items),
            limit=limit,
            offset=offset,
            total=len(farm_items),
        ),
    )
