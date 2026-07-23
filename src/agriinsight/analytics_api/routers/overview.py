from __future__ import annotations

from fastapi import APIRouter, Depends, Request

from agriinsight.analytics_api.auth_scope import AnalyticsArea
from agriinsight.analytics_api.dependencies import (
    RequestScopeResolver,
    request_scope_resolver,
)
from agriinsight.analytics_api.models import AnalyticsEnvelope, OverviewPayload
from agriinsight.analytics_api.read_models import overview_payload
from agriinsight.analytics_api.response_envelope import envelope
from agriinsight.analytics_api.routers.common import (
    assert_snapshot_current,
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
    resolver: RequestScopeResolver = Depends(request_scope_resolver),
) -> AnalyticsEnvelope[OverviewPayload]:
    scope = await resolver.authorize(AnalyticsArea.OVERVIEW)
    snapshot = verified_snapshot(request, scope)
    payload, partial, missing = overview_payload(snapshot, scope)
    response = envelope(
        snapshot,
        scope,
        payload,
        request.app.state.settings.max_artifact_age_hours,
        partial=partial,
        missing=missing,
    )
    assert_snapshot_current(request, snapshot)
    return response
