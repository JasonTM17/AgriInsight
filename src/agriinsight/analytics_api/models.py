from __future__ import annotations

from datetime import datetime
from typing import Generic, Literal, TypeAlias, TypeVar

from pydantic import BaseModel, ConfigDict, Field, JsonValue

from agriinsight.analytics_api.record_models import (
    CatalogFarmModel,
    CatalogWarehouseModel,
    CostBreakdownModel,
    CostCapabilitiesModel,
    CostFarmModel,
    CostMonthlyModel,
    CostSummaryModel,
    CropHealthAlertModel,
    CropHealthSummaryModel,
    CropProfitabilityModel,
    ExecutiveSummaryModel,
    FarmPerformanceModel,
    FarmScopeSummaryModel,
    FieldHealthModel,
    InsightModel,
    InventoryAbcModel,
    InventoryAlertModel,
    InventoryStatusModel,
    InventorySummaryModel,
    MonthlyFinancialModel,
    PestIncidentModel,
    QualityChecksModel,
    QualityRemediationModel,
    QualityScoresModel,
    RiskAlertModel,
)


def _camel(value: str) -> str:
    head, *tail = value.split("_")
    return head + "".join(part.capitalize() for part in tail)


class ApiModel(BaseModel):
    model_config = ConfigDict(
        alias_generator=_camel,
        extra="forbid",
        populate_by_name=True,
    )


class AppliedFilterModel(ApiModel):
    crop_code: str | None = None
    date_from: str | None = None
    date_preset: Literal["all", "last-30-days", "season-to-date"]
    date_to: str
    farm_code: str | None = None
    field_code: str | None = None
    season_code: str | None = None


class ScopeModel(ApiModel):
    applied_filter: AppliedFilterModel | None = None
    farm_codes: list[str] = Field(default_factory=list)
    tenant_id: str
    tenant_wide: bool
    warehouse_codes: list[str] = Field(default_factory=list)


class FreshnessModel(ApiModel):
    artifact_age_hours: float = Field(ge=0)
    data_status: Literal["current", "stale", "partial", "missing"]
    max_age_hours: int = Field(ge=1)


class LineageModel(ApiModel):
    as_of: str
    contract_version: Literal["1.0.0"] = "1.0.0"
    generated_at: datetime
    manifest_fingerprint: str
    run_id: str


class PageModel(ApiModel):
    has_more: bool
    limit: int = Field(ge=1, le=100)
    offset: int = Field(ge=0, le=10_000)
    total: int = Field(ge=0, le=10_000)


Severity: TypeAlias = Literal["none", "low", "medium", "high"]


class EvidenceSignalModel(ApiModel):
    name: str
    unit: str | None = None
    value: JsonValue


class CatalogPayload(ApiModel):
    allowed_farms: list[CatalogFarmModel]
    allowed_warehouses: list[CatalogWarehouseModel]
    locale: Literal["vi-VN"] = "vi-VN"


class OverviewPayload(ApiModel):
    insights: list[InsightModel]
    monthly_trend: list[MonthlyFinancialModel]
    summary: ExecutiveSummaryModel | FarmScopeSummaryModel
    top_risks: list[RiskAlertModel]


class FarmsPayload(ApiModel):
    crop_profitability: list[CropProfitabilityModel]
    items: list[FarmPerformanceModel]
    page: PageModel


class InventoryPayload(ApiModel):
    abc: list[InventoryAbcModel]
    alerts: list[InventoryAlertModel]
    items: list[InventoryStatusModel]
    page: PageModel
    summary: InventorySummaryModel


class CropHealthPayload(ApiModel):
    alerts: list[CropHealthAlertModel]
    assessment_method: Literal["rule-based-heuristic"] = "rule-based-heuristic"
    evidence_signals: list[EvidenceSignalModel]
    fields: list[FieldHealthModel]
    page: PageModel
    pest_incidents_weekly: list[PestIncidentModel]
    severity: Severity
    summary: CropHealthSummaryModel


class DataQualityPayload(ApiModel):
    checks: QualityChecksModel
    evidence_signals: list[EvidenceSignalModel]
    remediation_actions: QualityRemediationModel
    scores: QualityScoresModel
    severity: Severity
    status: Literal["passed", "failed"]


class CostsPayload(ApiModel):
    breakdown: list[CostBreakdownModel]
    capabilities: CostCapabilitiesModel
    farms: list[CostFarmModel]
    monthly: list[CostMonthlyModel]
    summary: CostSummaryModel


PayloadT = TypeVar("PayloadT", bound=BaseModel)


class AnalyticsEnvelope(ApiModel, Generic[PayloadT]):
    freshness: FreshnessModel
    lineage: LineageModel
    payload: PayloadT
    scope: ScopeModel


class ErrorDetail(ApiModel):
    code: str
    message: str


class ErrorEnvelope(ApiModel):
    correlation_id: str
    error: ErrorDetail
