from __future__ import annotations

from contextlib import asynccontextmanager
from typing import AsyncIterator

from fastapi import APIRouter, FastAPI, Request

from agriinsight.analytics_api.errors import install_error_boundary
from agriinsight.analytics_api.models import ErrorEnvelope
from agriinsight.analytics_api.reconciliation_gate import require_reconciliation
from agriinsight.analytics_api.routers import (
    catalog,
    costs,
    crop_health,
    data_quality,
    farms,
    inventory,
    overview,
)
from agriinsight.analytics_api.settings import AnalyticsSettings
from agriinsight.analytics_api.snapshot_cache import SnapshotCache
from agriinsight.analytics_api.spring_scope_client import SpringScopeClient

_ERROR_RESPONSES = {
    status: {"model": ErrorEnvelope}
    for status in (401, 403, 422, 500, 502, 503)
}


def create_app(
    settings: AnalyticsSettings | None = None,
    *,
    spring_client: SpringScopeClient | None = None,
) -> FastAPI:
    resolved = (settings or AnalyticsSettings.from_environment()).validated()
    spring = spring_client or SpringScopeClient(resolved)

    @asynccontextmanager
    async def lifespan(_app: FastAPI) -> AsyncIterator[None]:
        yield
        await spring.close()

    app = FastAPI(
        title="AgriInsight Internal Analytics API",
        version="1.0.0",
        description=(
            "Read-only, demo-tenant-gated analytics over checksum-verified "
            "aggregate snapshots."
        ),
        docs_url="/internal/docs",
        openapi_url="/internal/openapi.json",
        redoc_url=None,
        lifespan=lifespan,
    )
    app.state.settings = resolved
    app.state.snapshot_cache = SnapshotCache(resolved.artifact_root)
    app.state.spring_client = spring
    install_error_boundary(app)

    internal = APIRouter(
        prefix="/internal/v1",
        responses=_ERROR_RESPONSES,
    )
    for route in (
        catalog.router,
        overview.router,
        farms.router,
        inventory.router,
        crop_health.router,
        data_quality.router,
        costs.router,
    ):
        internal.include_router(route)
    app.include_router(internal)

    @app.get(
        "/health/live",
        include_in_schema=False,
    )
    async def live() -> dict[str, str]:
        return {"status": "alive"}

    @app.get(
        "/health/ready",
        operation_id="getAnalyticsReadiness",
        responses={503: {"model": ErrorEnvelope}},
    )
    async def ready(request: Request) -> dict[str, str]:
        snapshot = request.app.state.snapshot_cache.current()
        require_reconciliation(
            resolved.reconciliation_report,
            resolved.demo_tenant_id,
            snapshot,
            resolved.max_reconciliation_age_hours,
        )
        request.app.state.snapshot_cache.assert_current(snapshot)
        return {
            "runId": str(snapshot.manifest.get("run_id", "")),
            "status": "ready",
        }

    return app
