from __future__ import annotations

import os
import math
from dataclasses import dataclass, replace
from pathlib import Path
from typing import Mapping
from urllib.parse import urlsplit
from uuid import UUID


class AnalyticsSettingsError(ValueError):
    """Raised when the internal analytics runtime is not fail-closed."""


@dataclass(frozen=True, slots=True)
class AnalyticsSettings:
    artifact_root: Path
    demo_tenant_id: UUID
    reconciliation_report: Path
    spring_base_url: str
    connect_timeout_seconds: float = 2.0
    max_artifact_age_hours: int = 48
    max_reconciliation_age_hours: int = 24
    max_upstream_bytes: int = 262_144
    read_timeout_seconds: float = 4.0
    upstream_attempts: int = 2

    @classmethod
    def from_environment(
        cls, source: Mapping[str, str] | None = None
    ) -> AnalyticsSettings:
        values = source if source is not None else os.environ
        artifact_root = _required(values, "AGRIINSIGHT_ANALYTICS_ARTIFACT_ROOT")
        tenant_id = _required(values, "AGRIINSIGHT_ANALYTICS_DEMO_TENANT_ID")
        reconciliation = _required(
            values, "AGRIINSIGHT_ANALYTICS_RECONCILIATION_REPORT"
        )
        spring_url = _required(values, "AGRIINSIGHT_ANALYTICS_SPRING_BASE_URL")
        return cls(
            artifact_root=Path(artifact_root).resolve(),
            demo_tenant_id=UUID(tenant_id),
            reconciliation_report=Path(reconciliation).resolve(),
            spring_base_url=_validated_spring_url(spring_url),
            connect_timeout_seconds=_float(
                values, "AGRIINSIGHT_ANALYTICS_CONNECT_TIMEOUT_SECONDS", 2.0
            ),
            max_artifact_age_hours=_integer(
                values, "AGRIINSIGHT_ANALYTICS_MAX_ARTIFACT_AGE_HOURS", 48
            ),
            max_reconciliation_age_hours=_integer(
                values,
                "AGRIINSIGHT_ANALYTICS_MAX_RECONCILIATION_AGE_HOURS",
                24,
            ),
            max_upstream_bytes=_integer(
                values, "AGRIINSIGHT_ANALYTICS_MAX_UPSTREAM_BYTES", 262_144
            ),
            read_timeout_seconds=_float(
                values, "AGRIINSIGHT_ANALYTICS_READ_TIMEOUT_SECONDS", 4.0
            ),
            upstream_attempts=_integer(
                values, "AGRIINSIGHT_ANALYTICS_UPSTREAM_ATTEMPTS", 2
            ),
        ).validated()

    def validated(self) -> AnalyticsSettings:
        normalized_spring_url = _validated_spring_url(self.spring_base_url)
        if (
            not math.isfinite(self.connect_timeout_seconds)
            or not math.isfinite(self.read_timeout_seconds)
            or self.connect_timeout_seconds <= 0
            or self.read_timeout_seconds <= 0
        ):
            raise AnalyticsSettingsError("Upstream timeouts must be positive")
        if not 1 <= self.upstream_attempts <= 3:
            raise AnalyticsSettingsError("Upstream attempts must be between 1 and 3")
        if not 16_384 <= self.max_upstream_bytes <= 1_048_576:
            raise AnalyticsSettingsError("Upstream payload cap is outside safe bounds")
        if not 1 <= self.max_artifact_age_hours <= 168:
            raise AnalyticsSettingsError("Artifact age threshold is outside safe bounds")
        if not 1 <= self.max_reconciliation_age_hours <= 168:
            raise AnalyticsSettingsError(
                "Reconciliation age threshold is outside safe bounds"
            )
        return replace(self, spring_base_url=normalized_spring_url)


def _required(source: Mapping[str, str], key: str) -> str:
    value = source.get(key, "").strip()
    if not value:
        raise AnalyticsSettingsError(f"{key} is required")
    return value


def _validated_spring_url(value: str) -> str:
    parsed = urlsplit(value)
    if (
        parsed.scheme not in {"http", "https"}
        or not parsed.hostname
        or parsed.username
        or parsed.password
        or parsed.query
        or parsed.fragment
        or parsed.path not in {"", "/"}
    ):
        raise AnalyticsSettingsError("Spring base URL must be one fixed HTTP origin")
    return value.rstrip("/")


def _integer(source: Mapping[str, str], key: str, default: int) -> int:
    try:
        return int(source.get(key, str(default)))
    except ValueError as error:
        raise AnalyticsSettingsError(f"{key} must be an integer") from error


def _float(source: Mapping[str, str], key: str, default: float) -> float:
    try:
        return float(source.get(key, str(default)))
    except ValueError as error:
        raise AnalyticsSettingsError(f"{key} must be numeric") from error
