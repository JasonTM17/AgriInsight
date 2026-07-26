from __future__ import annotations

from typing import Literal

from fastapi import APIRouter, Depends, Query, Request

from agriinsight.analytics_api.auth_scope import AnalyticsArea
from agriinsight.analytics_api.dependencies import (
    RequestScopeResolver,
    request_scope_resolver,
)
from agriinsight.analytics_api.filter_scope import resolve_analytics_filter
from agriinsight.analytics_api.models import AnalyticsEnvelope, OverviewPayload
from agriinsight.analytics_api.read_models import overview_payload
from agriinsight.analytics_api.response_envelope import envelope
from agriinsight.analytics_api.routers.common import (
    assert_snapshot_current,
    require_farm_filter,
    verified_snapshot,
)

router = APIRouter(tags=["analytics-overview"])


@router.get(
    "/overview",
    operation_id="getAnalyticsOverview",
    response_model=AnalyticsEnvelope[OverviewPayload],
)
async def get_overview(
    request: Request,
    farm_code: str | None = Query(default=None, max_length=64),
    field_code: str | None = Query(default=None, max_length=64),
    crop_code: str | None = Query(default=None, max_length=64),
    season_code: str | None = Query(default=None, max_length=64),
    date_preset: Literal[
        "all", "last-30-days", "season-to-date"
    ] = "all",
    resolver: RequestScopeResolver = Depends(request_scope_resolver),
) -> AnalyticsEnvelope[OverviewPayload]:
    scope = await resolver.authorize(AnalyticsArea.OVERVIEW)
    require_farm_filter(scope, farm_code)
    snapshot = verified_snapshot(request, scope)
    applied_filter = resolve_analytics_filter(
        snapshot,
        scope,
        farm_code=farm_code,
        field_code=field_code,
        crop_code=crop_code,
        season_code=season_code,
        date_preset=date_preset,
    )
    payload, partial, missing = overview_payload(
        snapshot,
        scope,
        applied_filter,
    )
    response = envelope(
        snapshot,
        scope,
        payload,
        request.app.state.settings.max_artifact_age_hours,
        partial=partial,
        missing=missing,
        applied_filter=applied_filter.response_model(),
    )
    assert_snapshot_current(request, snapshot)
    return response
