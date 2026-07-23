from __future__ import annotations

import json
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Any
from uuid import UUID

from agriinsight.analytics_api.errors import ApiProblem
from agriinsight.analytics_snapshot import ArtifactSnapshot

_RECONCILED_DOMAINS = frozenset(
    {"crops", "farms", "fields", "materials", "personas", "seasons", "warehouses"}
)
MAX_RECONCILIATION_BYTES = 8 * 1024 * 1024


def require_reconciliation(
    report_path: Path,
    demo_tenant_id: UUID,
    snapshot: ArtifactSnapshot,
    max_age_hours: int,
) -> dict[str, Any]:
    try:
        if report_path.stat().st_size > MAX_RECONCILIATION_BYTES:
            raise ValueError("Reconciliation report exceeds the safe byte limit")
        payload = json.loads(report_path.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, ValueError, json.JSONDecodeError) as error:
        raise _not_ready() from error
    if not isinstance(payload, dict):
        raise _not_ready()
    expected = {
        "demoTenantId": str(demo_tenant_id),
        "manifestFingerprint": snapshot.manifest_fingerprint,
        "runId": str(snapshot.manifest.get("run_id", "")),
        "status": "passed",
    }
    if any(payload.get(key) != value for key, value in expected.items()):
        raise _not_ready()
    try:
        generated_at = datetime.fromisoformat(
            str(payload["generatedAt"]).replace("Z", "+00:00")
        )
        if generated_at.tzinfo is None:
            generated_at = generated_at.replace(tzinfo=timezone.utc)
        generated_at = generated_at.astimezone(timezone.utc)
    except (KeyError, TypeError, ValueError):
        raise _not_ready()
    now = datetime.now(timezone.utc)
    if generated_at > now + timedelta(minutes=5) or now - generated_at > timedelta(
        hours=max_age_hours
    ):
        raise _not_ready()
    domains = payload.get("domains")
    counts = payload.get("counts")
    if (
        payload.get("errorCount") != 0
        or not isinstance(domains, dict)
        or not isinstance(counts, dict)
        or set(domains) != _RECONCILED_DOMAINS
        or set(counts) != _RECONCILED_DOMAINS
    ):
        raise _not_ready()
    for domain in _RECONCILED_DOMAINS:
        result = domains.get(domain)
        if (
            not isinstance(result, dict)
            or result.get("errors") != []
            or result.get("actualCount") != counts.get(domain)
            or result.get("expectedCount") != counts.get(domain)
        ):
            raise _not_ready()
    return payload


def _not_ready() -> ApiProblem:
    return ApiProblem(
        503,
        "reconciliation_required",
        "Analytics canonical masters are not reconciled for this snapshot.",
    )
