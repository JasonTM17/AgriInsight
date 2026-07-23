from __future__ import annotations

from fastapi import APIRouter, Depends, Request

from agriinsight.analytics_api.auth_scope import AnalyticsArea
from agriinsight.analytics_api.dependencies import (
    RequestScopeResolver,
    request_scope_resolver,
)
from agriinsight.analytics_api.cost_quality_read_models import data_quality_payload
from agriinsight.analytics_api.models import AnalyticsEnvelope, DataQualityPayload
from agriinsight.analytics_api.response_envelope import envelope
from agriinsight.analytics_api.routers.common import (
    assert_snapshot_current,
    verified_snapshot,
)

router = APIRouter(tags=["analytics-data-quality"])


@router.get(
    "/data-quality",
    operation_id="getAnalyticsDataQuality",
    response_model=AnalyticsEnvelope[DataQualityPayload],
)
async def get_data_quality(
    request: Request,
    resolver: RequestScopeResolver = Depends(request_scope_resolver),
) -> AnalyticsEnvelope[DataQualityPayload]:
    scope = await resolver.authorize(AnalyticsArea.DATA_QUALITY)
    snapshot = verified_snapshot(request)
    response = envelope(
        snapshot,
        scope,
        data_quality_payload(snapshot),
        request.app.state.settings.max_artifact_age_hours,
    )
    assert_snapshot_current(request, snapshot)
    return response
