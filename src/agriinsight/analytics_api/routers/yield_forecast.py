from __future__ import annotations

from fastapi import APIRouter, Depends, Query, Request

from agriinsight.analytics_api.auth_scope import AnalyticsArea
from agriinsight.analytics_api.dependencies import (
    RequestScopeResolver,
    request_scope_resolver,
)
from agriinsight.analytics_api.filter_scope import resolve_analytics_filter
from agriinsight.analytics_api.models import AnalyticsEnvelope, YieldForecastPayload
from agriinsight.analytics_api.response_bounds import (
    require_serialized_response_within_limit,
)
from agriinsight.analytics_api.response_envelope import envelope
from agriinsight.analytics_api.routers.common import (
    assert_snapshot_current,
    require_farm_filter,
    verified_snapshot,
)
from agriinsight.analytics_api.yield_forecast_read_models import (
    yield_forecast_payload,
)
from agriinsight.yield_forecast_input_validation import IDENTIFIER_PATTERN

router = APIRouter(tags=["analytics-yield-forecast"])


@router.get(
    "/yield-forecast",
    operation_id="getAnalyticsYieldForecast",
    response_model=AnalyticsEnvelope[YieldForecastPayload],
)
async def get_yield_forecast(
    request: Request,
    farm_code: str | None = Query(
        default=None,
        min_length=1,
        max_length=64,
        pattern=IDENTIFIER_PATTERN.pattern,
    ),
    field_code: str | None = Query(
        default=None,
        min_length=1,
        max_length=64,
        pattern=IDENTIFIER_PATTERN.pattern,
    ),
    crop_code: str | None = Query(
        default=None,
        min_length=1,
        max_length=64,
        pattern=IDENTIFIER_PATTERN.pattern,
    ),
    season_code: str | None = Query(
        default=None,
        min_length=1,
        max_length=64,
        pattern=IDENTIFIER_PATTERN.pattern,
    ),
    limit: int = Query(default=50, ge=1, le=100),
    offset: int = Query(default=0, ge=0, le=10_000),
    resolver: RequestScopeResolver = Depends(request_scope_resolver),
) -> AnalyticsEnvelope[YieldForecastPayload]:
    scope = await resolver.authorize(AnalyticsArea.FARMS)
    require_farm_filter(scope, farm_code)
    snapshot = verified_snapshot(request, scope)
    applied_filter = resolve_analytics_filter(
        snapshot,
        scope,
        farm_code=farm_code,
        field_code=field_code,
        crop_code=crop_code,
        season_code=season_code,
        date_preset="all",
    )
    payload = yield_forecast_payload(
        snapshot,
        scope,
        applied_filter=applied_filter,
        limit=limit,
        offset=offset,
    )
    response = envelope(
        snapshot,
        scope,
        payload,
        request.app.state.settings.max_artifact_age_hours,
        missing=payload.page.total == 0,
        applied_filter=applied_filter.response_model(),
    )
    assert_snapshot_current(request, snapshot)
    require_serialized_response_within_limit(response)
    return response
