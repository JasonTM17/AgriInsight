from __future__ import annotations

from fastapi import APIRouter, Depends, Request

from agriinsight.analytics_api.auth_scope import AnalyticsArea
from agriinsight.analytics_api.dependencies import (
    RequestScopeResolver,
    request_scope_resolver,
)
from agriinsight.analytics_api.cost_quality_read_models import costs_payload
from agriinsight.analytics_api.models import AnalyticsEnvelope, CostsPayload
from agriinsight.analytics_api.response_envelope import envelope
from agriinsight.analytics_api.routers.common import (
    assert_snapshot_current,
    verified_snapshot,
)

router = APIRouter(tags=["analytics-costs"])


@router.get(
    "/costs",
    operation_id="getAnalyticsCosts",
    response_model=AnalyticsEnvelope[CostsPayload],
)
async def get_costs(
    request: Request,
    resolver: RequestScopeResolver = Depends(request_scope_resolver),
) -> AnalyticsEnvelope[CostsPayload]:
    scope = await resolver.authorize(AnalyticsArea.COSTS)
    snapshot = verified_snapshot(request, scope)
    payload, partial = costs_payload(snapshot, scope)
    response = envelope(
        snapshot,
        scope,
        payload,
        request.app.state.settings.max_artifact_age_hours,
        partial=partial,
        missing=not payload.farms,
    )
    assert_snapshot_current(request, snapshot)
    return response
