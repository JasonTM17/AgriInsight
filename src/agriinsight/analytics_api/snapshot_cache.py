from __future__ import annotations

import hashlib
from datetime import datetime, timedelta, timezone
from pathlib import Path
from threading import RLock

import numpy as np
import pandas as pd
from pydantic import ValidationError

from agriinsight.analytics_api.errors import ApiProblem
from agriinsight.analytics_api.record_models import (
    CostBreakdownModel,
    CostFarmModel,
    CostMonthlyModel,
    CostSummaryModel,
    CropHealthAlertModel,
    CropProfitabilityModel,
    ExecutiveSummaryModel,
    FarmPerformanceModel,
    FieldHealthModel,
    InsightModel,
    InventoryAlertModel,
    InventoryStatusModel,
    MonthlyFinancialModel,
    PestIncidentModel,
    QualityChecksModel,
    QualityRemediationModel,
    QualityScoresModel,
    RiskAlertModel,
)
from agriinsight.analytics_api.response_shaping import records
from agriinsight.analytics_snapshot import (
    ArtifactSnapshot,
    ArtifactSnapshotError,
    MAX_MANIFEST_BYTES,
    load_artifact_snapshot,
)

AGGREGATE_CSV_DATASETS = {
    "cost_breakdown": "gold/cost_breakdown.csv",
    "cost_farm": "gold/cost_farm.csv",
    "cost_monthly": "gold/cost_monthly.csv",
    "cost_season": "gold/cost_season.csv",
    "cost_summary": "gold/cost_summary.csv",
    "crop_health_alerts": "gold/crop_health_alerts.csv",
    "crop_profitability": "gold/crop_profitability.csv",
    "executive_summary": "gold/executive_summary.csv",
    "farm_performance": "gold/farm_performance.csv",
    "field_health_status": "gold/field_health_status.csv",
    "inventory_alerts": "gold/inventory_alerts.csv",
    "inventory_status": "gold/inventory_status.csv",
    "monthly_financials": "gold/monthly_financials.csv",
    "pest_incidents_weekly": "gold/pest_incidents_weekly.csv",
    "risk_alerts": "gold/risk_alerts.csv",
}
AGGREGATE_JSON_DATASETS = {
    "insights": "gold/insights.json",
    "quality": "quality/data_quality_report.json",
}
EXPECTED_COLUMNS = {
    "cost_breakdown": set(CostBreakdownModel.model_fields),
    "cost_farm": set(CostFarmModel.model_fields),
    "cost_monthly": set(CostMonthlyModel.model_fields),
    "cost_season": {
        "area_ha",
        "budget_operating_cost_vnd",
        "budget_variance_pct",
        "budget_variance_vnd",
        "crop_code",
        "crop_name",
        "expected_harvest_date",
        "farm_code",
        "farm_name",
        "field_code",
        "field_name",
        "harvest_quantity_kg",
        "operating_cost_per_ha_vnd",
        "operating_cost_per_kg_vnd",
        "operating_labor_cost_vnd",
        "operating_material_cost_vnd",
        "operating_profit_margin_pct",
        "operating_profit_vnd",
        "operating_total_cost_vnd",
        "revenue_vnd",
        "season_code",
        "season_status",
        "start_date",
        "target_yield_kg",
    },
    "cost_summary": set(CostSummaryModel.model_fields),
    "crop_health_alerts": set(CropHealthAlertModel.model_fields),
    "crop_profitability": set(CropProfitabilityModel.model_fields),
    "executive_summary": set(ExecutiveSummaryModel.model_fields),
    "farm_performance": set(FarmPerformanceModel.model_fields),
    "field_health_status": set(FieldHealthModel.model_fields),
    "inventory_alerts": set(InventoryAlertModel.model_fields),
    "inventory_status": set(InventoryStatusModel.model_fields),
    "monthly_financials": set(MonthlyFinancialModel.model_fields),
    "pest_incidents_weekly": set(PestIncidentModel.model_fields),
    "risk_alerts": set(RiskAlertModel.model_fields),
}
CSV_MODELS = {
    "cost_breakdown": CostBreakdownModel,
    "cost_farm": CostFarmModel,
    "cost_monthly": CostMonthlyModel,
    "cost_summary": CostSummaryModel,
    "crop_health_alerts": CropHealthAlertModel,
    "crop_profitability": CropProfitabilityModel,
    "executive_summary": ExecutiveSummaryModel,
    "farm_performance": FarmPerformanceModel,
    "field_health_status": FieldHealthModel,
    "inventory_alerts": InventoryAlertModel,
    "inventory_status": InventoryStatusModel,
    "monthly_financials": MonthlyFinancialModel,
    "pest_incidents_weekly": PestIncidentModel,
    "risk_alerts": RiskAlertModel,
}
MAX_ROWS = {
    "cost_breakdown": 100,
    "cost_farm": 1_000,
    "cost_monthly": 600,
    "cost_season": 10_000,
    "cost_summary": 1,
    "crop_health_alerts": 1_000,
    "crop_profitability": 500,
    "executive_summary": 1,
    "farm_performance": 10_000,
    "field_health_status": 10_000,
    "inventory_alerts": 1_000,
    "inventory_status": 10_000,
    "monthly_financials": 600,
    "pest_incidents_weekly": 2_000,
    "risk_alerts": 1_000,
}


class SnapshotCache:
    """Process-local immutable cache keyed by verified manifest bytes."""

    def __init__(self, artifact_root: Path) -> None:
        self._artifact_root = artifact_root.resolve()
        self._cached: ArtifactSnapshot | None = None
        self._lock = RLock()

    def current(self) -> ArtifactSnapshot:
        with self._lock:
            fingerprint = self._manifest_fingerprint()
            if (
                self._cached is not None
                and self._cached.manifest_fingerprint == fingerprint
            ):
                return self._cached
            try:
                loaded = load_artifact_snapshot(
                    self._artifact_root,
                    csv_datasets=AGGREGATE_CSV_DATASETS,
                    json_datasets=AGGREGATE_JSON_DATASETS,
                )
            except ArtifactSnapshotError as error:
                raise ApiProblem(
                    503,
                    "snapshot_unavailable",
                    "A verified analytics snapshot is unavailable.",
                ) from error
            _validate_snapshot(loaded)
            self._cached = loaded
            return loaded

    def assert_current(self, snapshot: ArtifactSnapshot) -> None:
        if self._manifest_fingerprint() != snapshot.manifest_fingerprint:
            raise ApiProblem(
                503,
                "snapshot_changed",
                "The analytics snapshot changed during the request.",
            )

    def _manifest_fingerprint(self) -> str:
        manifest_path = self._artifact_root / "manifest.json"
        try:
            if manifest_path.stat().st_size > MAX_MANIFEST_BYTES:
                raise ValueError("Manifest exceeds the safe byte limit")
            content = manifest_path.read_bytes()
        except (OSError, ValueError) as error:
            raise ApiProblem(
                503,
                "snapshot_unavailable",
                "A verified analytics snapshot is unavailable.",
            ) from error
        return hashlib.sha256(content).hexdigest()


def _validate_snapshot(snapshot: ArtifactSnapshot) -> None:
    invalid = any(
        set(snapshot.csv[name].columns) != expected
        or len(snapshot.csv[name]) > MAX_ROWS[name]
        for name, expected in EXPECTED_COLUMNS.items()
    )
    invalid = invalid or any(
        not _valid_csv_records(snapshot.csv[name], model)
        for name, model in CSV_MODELS.items()
    )
    invalid = invalid or not _valid_cost_season(snapshot.csv["cost_season"])
    insights = snapshot.json.get("insights")
    quality = snapshot.json.get("quality")
    invalid = invalid or not isinstance(insights, dict)
    invalid = invalid or not isinstance(insights.get("insights"), list)
    invalid = invalid or not isinstance(quality, dict)
    invalid = invalid or not all(
        key in quality
        for key in ("checks", "remediation_actions", "scores", "status")
    )
    invalid = invalid or not all(
        isinstance(snapshot.manifest.get(key), str)
        and bool(snapshot.manifest.get(key))
        for key in ("as_of_date", "run_id")
    )
    invalid = invalid or snapshot.generated_at > (
        datetime.now(timezone.utc) + timedelta(minutes=5)
    )
    if not invalid:
        try:
            insight_items = insights["insights"]
            if len(insight_items) > 100:
                invalid = True
            else:
                for item in insight_items:
                    InsightModel.model_validate(item)
                QualityChecksModel.model_validate(quality["checks"])
                if any(
                    len(quality["checks"][stage]) > 1_000
                    for stage in ("before", "after")
                ):
                    invalid = True
                QualityRemediationModel.model_validate(
                    quality["remediation_actions"]
                )
                QualityScoresModel.model_validate(quality["scores"])
                if quality["status"] not in {"passed", "failed"}:
                    invalid = True
        except (KeyError, TypeError, ValidationError):
            invalid = True
    if invalid:
        raise ApiProblem(
            503,
            "snapshot_contract_invalid",
            "The verified analytics snapshot does not match the API contract.",
        )


def _valid_csv_records(frame, model: type) -> bool:
    try:
        for row in records(frame):
            model.model_validate(row)
    except (TypeError, ValueError, ValidationError):
        return False
    return True


def _valid_cost_season(frame) -> bool:
    numeric_columns = (
        "area_ha",
        "budget_operating_cost_vnd",
        "target_yield_kg",
        "harvest_quantity_kg",
        "revenue_vnd",
        "operating_material_cost_vnd",
        "operating_labor_cost_vnd",
        "operating_total_cost_vnd",
        "operating_profit_vnd",
        "operating_profit_margin_pct",
        "operating_cost_per_ha_vnd",
        "operating_cost_per_kg_vnd",
        "budget_variance_vnd",
        "budget_variance_pct",
    )
    if any(column not in frame.columns for column in numeric_columns):
        return False
    for column in numeric_columns:
        values = pd.to_numeric(frame[column], errors="coerce")
        if ((frame[column].notna()) & values.isna()).any():
            return False
        present = values[frame[column].notna()]
        if not np.isfinite(present.to_numpy(dtype=float)).all():
            return False
    return True
