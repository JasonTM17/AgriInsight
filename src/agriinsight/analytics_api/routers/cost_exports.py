"""Normalized single-format cost export.

The download is served from the same verified artifact snapshot as every other
analytics read, restricted to the caller's farm scope, and rendered one format
per request. Nothing here reveals a filesystem path: staging happens under the
artifact root's `_tmp` directory and only lineage plus sizing is returned.
"""

from __future__ import annotations

import json
import re
from pathlib import Path

import pandas as pd
from fastapi import APIRouter, Depends, Query, Request, Response

from agriinsight.analytics_api.auth_scope import AnalyticsArea, AuthorizedScope
from agriinsight.analytics_api.dependencies import (
    RequestScopeResolver,
    request_scope_resolver,
)
from agriinsight.analytics_api.errors import ApiProblem
from agriinsight.analytics_api.routers.common import (
    assert_snapshot_current,
    require_farm_filter,
    verified_snapshot,
)
from agriinsight.analytics_snapshot import ArtifactSnapshot
from agriinsight.cost_report_contract import ExportUnavailable, ReportValidationError
from agriinsight.cost_report_single_export import render_single_cost_export

router = APIRouter(tags=["analytics-cost-exports"])

EXPORT_DATASETS = ("cost_activity_detail", "procurement_detail")
METADATA_HEADER = "X-AgriInsight-Export-Metadata"
_SAFE_FILENAME = re.compile(r"^[A-Za-z0-9._-]{1,120}$")
_MONTH_PATTERN = r"^\d{4}-(0[1-9]|1[0-2])$"
_CODE_PATTERN = r"^[A-Za-z0-9][A-Za-z0-9_-]{0,63}$"


def _scoped_gold(
    snapshot: ArtifactSnapshot, scope: AuthorizedScope
) -> dict[str, pd.DataFrame]:
    """Restrict the export sources to farms the caller may already read."""

    frames: dict[str, pd.DataFrame] = {}
    for name in EXPORT_DATASETS:
        frame = snapshot.csv[name]
        if not scope.farm_tenant_wide:
            frame = frame[frame["farm_code"].isin(scope.farm_codes)]
        frames[name] = frame.copy()
    return frames


def _applied_filters(raw_request: dict[str, object]) -> str:
    if not raw_request:
        return "none"
    return ", ".join(f"{key}={value}" for key, value in sorted(raw_request.items()))


def _staging_root(request: Request) -> Path:
    return Path(request.app.state.settings.artifact_root) / "_tmp" / "cost-exports"


def _content_disposition(filename: str) -> str:
    if not _SAFE_FILENAME.fullmatch(filename):
        raise ApiProblem(
            500,
            "export_filename_unsafe",
            "The generated export filename was rejected before download.",
        )
    return f'attachment; filename="{filename}"'


@router.get(
    "/costs/export",
    operation_id="getAnalyticsCostExport",
    response_class=Response,
)
async def get_cost_export(
    request: Request,
    export_format: str = Query(alias="format"),
    report_scope: str = Query(default="all", alias="scope", pattern=_CODE_PATTERN),
    farm: str | None = Query(default=None, pattern=_CODE_PATTERN),
    crop: str | None = Query(default=None, pattern=_CODE_PATTERN),
    activity: str | None = Query(default=None, pattern=_CODE_PATTERN),
    supplier: str | None = Query(default=None, pattern=_CODE_PATTERN),
    season: str | None = Query(default=None, pattern=_CODE_PATTERN),
    month_from: str | None = Query(default=None, pattern=_MONTH_PATTERN),
    month_to: str | None = Query(default=None, pattern=_MONTH_PATTERN),
    top_n: int = Query(default=15, ge=1, le=30),
    resolver: RequestScopeResolver = Depends(request_scope_resolver),
) -> Response:
    scope = await resolver.authorize(AnalyticsArea.COSTS)
    require_farm_filter(scope, farm)
    if month_from and month_to and month_from > month_to:
        raise ApiProblem(
            422,
            "invalid_request",
            "month_from must not be after month_to.",
        )
    snapshot = verified_snapshot(request, scope)

    raw_request: dict[str, object] = {"scope": report_scope, "top_n": top_n}
    for key, value in (
        ("farm", farm),
        ("crop", crop),
        ("activity", activity),
        ("supplier", supplier),
        ("season", season),
        ("month_from", month_from),
        ("month_to", month_to),
    ):
        if value is not None:
            raw_request[key] = value

    try:
        export = render_single_cost_export(
            _scoped_gold(snapshot, scope),
            snapshot.manifest,
            raw_request,
            export_format=export_format,
            temp_root=_staging_root(request),
        )
    except ReportValidationError as error:
        raise ApiProblem(
            422,
            "export_rejected",
            f"{error} Applied filters: {_applied_filters(raw_request)}.",
        ) from error
    except ExportUnavailable as error:
        raise ApiProblem(
            503,
            "export_format_unavailable",
            f"{error} Request csv or pdf instead.",
        ) from error

    assert_snapshot_current(request, snapshot)
    return Response(
        content=export.artifact.content,
        media_type=export.artifact.mime_type,
        headers={
            "Content-Disposition": _content_disposition(export.artifact.filename),
            "Content-Length": str(export.byte_size),
            METADATA_HEADER: json.dumps(
                export.safe_metadata(), separators=(",", ":"), sort_keys=True
            ),
        },
    )


__all__ = ["router"]
