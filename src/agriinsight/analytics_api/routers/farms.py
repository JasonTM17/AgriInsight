from __future__ import annotations

from typing import Literal

from fastapi import APIRouter, Depends, Query, Request

from agriinsight.analytics_api.auth_scope import AnalyticsArea
from agriinsight.analytics_api.dependencies import (
    RequestScopeResolver,
    request_scope_resolver,
)
from agriinsight.analytics_api.models import AnalyticsEnvelope, FarmsPayload
from agriinsight.analytics_api.read_models import farms_payload
from agriinsight.analytics_api.response_envelope import envelope
from agriinsight.analytics_api.routers.common import (
    assert_snapshot_current,
    require_farm_filter,
    verified_snapshot,
)

router = APIRouter(tags=["analytics-farms"])


@router.get(
    "/farms",
    operation_id="getAnalyticsFarms",
    response_model=AnalyticsEnvelope[FarmsPayload],
)
async def get_farms(
    request: Request,
    farm_code: str | None = Query(default=None, max_length=64),
    limit: int = Query(default=50, ge=1, le=100),
    offset: int = Query(default=0, ge=0, le=10_000),
    sort: Literal["farm_code", "profit_desc"] = "farm_code",
    resolver: RequestScopeResolver = Depends(request_scope_resolver),
) -> AnalyticsEnvelope[FarmsPayload]:
    scope = await resolver.authorize(AnalyticsArea.FARMS)
    require_farm_filter(scope, farm_code)
    snapshot = verified_snapshot(request, scope)
    payload = farms_payload(
        snapshot,
        scope,
        farm_code=farm_code,
        limit=limit,
        offset=offset,
        sort=sort,
    )
    response = envelope(
        snapshot,
        scope,
        payload,
        request.app.state.settings.max_artifact_age_hours,
        missing=payload.page.total == 0,
    )
    assert_snapshot_current(request, snapshot)
    return response
