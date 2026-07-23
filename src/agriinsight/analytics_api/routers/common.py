from __future__ import annotations

from fastapi import Request

from agriinsight.analytics_api.auth_scope import AuthorizedScope
from agriinsight.analytics_api.errors import ApiProblem
from agriinsight.analytics_api.reconciliation_gate import require_reconciliation
from agriinsight.analytics_snapshot import ArtifactSnapshot


def verified_snapshot(
    request: Request,
    scope: AuthorizedScope | None = None,
) -> ArtifactSnapshot:
    snapshot = request.app.state.snapshot_cache.current()
    require_reconciliation(
        request.app.state.settings.reconciliation_report,
        request.app.state.settings.demo_tenant_id,
        snapshot,
        request.app.state.settings.max_reconciliation_age_hours,
    )
    if scope is not None:
        _require_live_scope_alignment(snapshot, scope)
    return snapshot


def assert_snapshot_current(
    request: Request,
    snapshot: ArtifactSnapshot,
) -> None:
    request.app.state.snapshot_cache.assert_current(snapshot)


def require_farm_filter(
    scope: AuthorizedScope,
    farm_code: str | None,
) -> None:
    if farm_code is not None and farm_code not in scope.farm_codes:
        raise ApiProblem(
            403,
            "farm_scope_forbidden",
            "The requested farm is outside the authenticated operational scope.",
        )


def require_warehouse_filter(
    scope: AuthorizedScope,
    warehouse_code: str | None,
) -> None:
    if warehouse_code is not None and warehouse_code not in scope.warehouse_codes:
        raise ApiProblem(
            403,
            "warehouse_scope_forbidden",
            "The requested warehouse is outside the authenticated operational scope.",
        )


def _require_live_scope_alignment(
    snapshot: ArtifactSnapshot,
    scope: AuthorizedScope,
) -> None:
    if scope.farm_tenant_wide:
        artifact_farms = frozenset(
            str(code) for code in snapshot.csv["farm_performance"]["farm_code"]
        )
        if scope.farm_codes != artifact_farms:
            raise _operational_scope_drift()
    if scope.warehouse_tenant_wide:
        artifact_warehouses = frozenset(
            str(code) for code in snapshot.csv["inventory_status"]["warehouse_code"]
        )
        if scope.warehouse_codes != artifact_warehouses:
            raise _operational_scope_drift()


def _operational_scope_drift() -> ApiProblem:
    return ApiProblem(
        503,
        "operational_scope_drift",
        "Operational master scope no longer matches this analytics snapshot.",
    )
