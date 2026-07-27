from __future__ import annotations

from fastapi import APIRouter, Depends, Query, Request

from agriinsight.analytics_api.auth_scope import AnalyticsArea
from agriinsight.analytics_api.dependencies import (
    RequestScopeResolver,
    request_scope_resolver,
)
from agriinsight.analytics_api.cost_quality_read_models import costs_payload
from agriinsight.analytics_api.errors import ApiProblem
from agriinsight.analytics_api.models import (
    AnalyticsEnvelope,
    CostsPayload,
    ProcurementCostsPayload,
)
from agriinsight.analytics_api.procurement_cost_read_models import (
    procurement_costs_payload,
)
from agriinsight.analytics_api.response_envelope import envelope
from agriinsight.analytics_api.routers.common import (
    assert_snapshot_current,
    require_farm_filter,
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


@router.get(
    "/costs/procurement",
    operation_id="getProcurementCosts",
    response_model=AnalyticsEnvelope[ProcurementCostsPayload],
)
async def get_procurement_costs(
    request: Request,
    farm_code: str | None = Query(
        default=None,
        min_length=1,
        max_length=64,
        pattern=r"^[A-Za-z0-9][A-Za-z0-9_-]{0,63}$",
    ),
    month_from: str | None = Query(
        default=None,
        pattern=r"^\d{4}-(0[1-9]|1[0-2])$",
    ),
    month_to: str | None = Query(
        default=None,
        pattern=r"^\d{4}-(0[1-9]|1[0-2])$",
    ),
    limit: int = Query(default=50, ge=1, le=100),
    offset: int = Query(default=0, ge=0, le=10_000),
    resolver: RequestScopeResolver = Depends(request_scope_resolver),
) -> AnalyticsEnvelope[ProcurementCostsPayload]:
    scope = await resolver.authorize(AnalyticsArea.COSTS)
    require_farm_filter(scope, farm_code)
    if month_from and month_to and month_from > month_to:
        raise ApiProblem(
            422,
            "invalid_request",
            "month_from must not be after month_to.",
        )
    snapshot = verified_snapshot(request, scope)
    payload, missing = procurement_costs_payload(
        snapshot,
        scope,
        farm_code=farm_code,
        month_from=month_from,
        month_to=month_to,
        limit=limit,
        offset=offset,
    )
    response = envelope(
        snapshot,
        scope,
        payload,
        request.app.state.settings.max_artifact_age_hours,
        missing=missing,
    )
    assert_snapshot_current(request, snapshot)
    return response
