from __future__ import annotations

from fastapi import APIRouter, Depends, Query, Request

from agriinsight.analytics_api.auth_scope import AnalyticsArea
from agriinsight.analytics_api.dependencies import (
    RequestScopeResolver,
    request_scope_resolver,
)
from agriinsight.analytics_api.domain_read_models import inventory_payload
from agriinsight.analytics_api.models import AnalyticsEnvelope, InventoryPayload
from agriinsight.analytics_api.response_envelope import envelope
from agriinsight.analytics_api.response_bounds import (
    require_serialized_response_within_limit,
)
from agriinsight.analytics_api.routers.common import (
    assert_snapshot_current,
    require_warehouse_filter,
    verified_snapshot,
)

router = APIRouter(tags=["analytics-inventory"])


@router.get(
    "/inventory",
    operation_id="getAnalyticsInventory",
    response_model=AnalyticsEnvelope[InventoryPayload],
)
async def get_inventory(
    request: Request,
    warehouse_code: str | None = Query(default=None, max_length=64),
    limit: int = Query(default=50, ge=1, le=100),
    offset: int = Query(default=0, ge=0, le=10_000),
    resolver: RequestScopeResolver = Depends(request_scope_resolver),
) -> AnalyticsEnvelope[InventoryPayload]:
    scope = await resolver.authorize(AnalyticsArea.INVENTORY)
    require_warehouse_filter(scope, warehouse_code)
    snapshot = verified_snapshot(request, scope)
    payload = inventory_payload(
        snapshot,
        scope,
        warehouse_code=warehouse_code,
        limit=limit,
        offset=offset,
    )
    response = envelope(
        snapshot,
        scope,
        payload,
        request.app.state.settings.max_artifact_age_hours,
        missing=payload.page.total == 0,
    )
    assert_snapshot_current(request, snapshot)
    require_serialized_response_within_limit(response)
    return response
