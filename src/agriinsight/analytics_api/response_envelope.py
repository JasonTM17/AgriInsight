from __future__ import annotations

from datetime import datetime, timezone
from typing import TypeVar

from pydantic import BaseModel

from agriinsight.analytics_api.auth_scope import AuthorizedScope
from agriinsight.analytics_api.models import (
    AnalyticsEnvelope,
    FreshnessModel,
    LineageModel,
    ScopeModel,
)
from agriinsight.analytics_snapshot import ArtifactSnapshot

Payload = TypeVar("Payload", bound=BaseModel)


def envelope(
    snapshot: ArtifactSnapshot,
    scope: AuthorizedScope,
    payload: Payload,
    max_age_hours: int,
    *,
    partial: bool = False,
    missing: bool = False,
) -> AnalyticsEnvelope[Payload]:
    age_hours = max(
        0.0,
        (datetime.now(timezone.utc) - snapshot.generated_at).total_seconds() / 3600,
    )
    if missing:
        status = "missing"
    elif partial:
        status = "partial"
    elif age_hours > max_age_hours:
        status = "stale"
    else:
        status = "current"
    manifest = snapshot.manifest
    return AnalyticsEnvelope(
        freshness=FreshnessModel(
            artifact_age_hours=round(age_hours, 3),
            data_status=status,
            max_age_hours=max_age_hours,
        ),
        lineage=LineageModel(
            as_of=str(manifest.get("as_of_date", "")),
            generated_at=snapshot.generated_at,
            manifest_fingerprint=snapshot.manifest_fingerprint,
            run_id=str(manifest.get("run_id", "")),
        ),
        payload=payload,
        scope=ScopeModel(
            farm_codes=sorted(scope.farm_codes),
            tenant_id=str(scope.tenant_id),
            tenant_wide=scope.tenant_wide,
            warehouse_codes=sorted(scope.warehouse_codes),
        ),
    )
