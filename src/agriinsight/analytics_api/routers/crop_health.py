from __future__ import annotations

from fastapi import APIRouter, Depends, Query, Request

from agriinsight.analytics_api.auth_scope import AnalyticsArea
from agriinsight.analytics_api.dependencies import (
    RequestScopeResolver,
    request_scope_resolver,
)
from agriinsight.analytics_api.domain_read_models import crop_health_payload
from agriinsight.analytics_api.models import AnalyticsEnvelope, CropHealthPayload
from agriinsight.analytics_api.response_envelope import envelope
from agriinsight.analytics_api.routers.common import (
    assert_snapshot_current,
    require_farm_filter,
    verified_snapshot,
)

router = APIRouter(tags=["analytics-crop-health"])


@router.get(
    "/crop-health",
    operation_id="getAnalyticsCropHealth",
    response_model=AnalyticsEnvelope[CropHealthPayload],
)
async def get_crop_health(
    request: Request,
    farm_code: str | None = Query(default=None, max_length=64),
    limit: int = Query(default=50, ge=1, le=100),
    offset: int = Query(default=0, ge=0, le=10_000),
    resolver: RequestScopeResolver = Depends(request_scope_resolver),
) -> AnalyticsEnvelope[CropHealthPayload]:
    scope = await resolver.authorize(AnalyticsArea.CROP_HEALTH)
    require_farm_filter(scope, farm_code)
    snapshot = verified_snapshot(request, scope)
    payload, partial = crop_health_payload(
        snapshot,
        scope,
        farm_code=farm_code,
        limit=limit,
        offset=offset,
    )
    response = envelope(
        snapshot,
        scope,
        payload,
        request.app.state.settings.max_artifact_age_hours,
        partial=partial,
        missing=payload.page.total == 0,
    )
    assert_snapshot_current(request, snapshot)
    return response
