from __future__ import annotations

from fastapi import APIRouter, Depends, Request

from agriinsight.analytics_api.auth_scope import AnalyticsArea
from agriinsight.analytics_api.dependencies import (
    RequestScopeResolver,
    request_scope_resolver,
)
from agriinsight.analytics_api.models import AnalyticsEnvelope, CatalogPayload
from agriinsight.analytics_api.read_models import catalog_payload
from agriinsight.analytics_api.response_envelope import envelope
from agriinsight.analytics_api.routers.common import (
    assert_snapshot_current,
    verified_snapshot,
)

router = APIRouter(tags=["analytics-catalog"])


@router.get(
    "/catalog",
    operation_id="getAnalyticsCatalog",
    response_model=AnalyticsEnvelope[CatalogPayload],
)
async def get_catalog(
    request: Request,
    resolver: RequestScopeResolver = Depends(request_scope_resolver),
) -> AnalyticsEnvelope[CatalogPayload]:
    scope = await resolver.authorize(AnalyticsArea.CATALOG)
    farms = await resolver.farm_items() if scope.farm_codes else []
    warehouses = (
        await resolver.warehouse_items() if scope.warehouse_codes else []
    )
    snapshot = verified_snapshot(request, scope)
    response = envelope(
        snapshot,
        scope,
        catalog_payload(farms, warehouses),
        request.app.state.settings.max_artifact_age_hours,
    )
    assert_snapshot_current(request, snapshot)
    return response
