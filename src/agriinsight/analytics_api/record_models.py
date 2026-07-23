from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, ConfigDict, JsonValue


def _camel(value: str) -> str:
    head, *tail = value.split("_")
    return head + "".join(part.capitalize() for part in tail)


class RecordModel(BaseModel):
    model_config = ConfigDict(
        alias_generator=_camel,
        extra="forbid",
        populate_by_name=True,
    )


class CatalogFarmModel(RecordModel):
    code: str
    display_name: str
    id: str


class CatalogWarehouseModel(CatalogFarmModel):
    location_text: str | None = None


class InsightEvidenceModel(RecordModel):
    activity_type: str | None = None
    alert_count: int | None = None
    critical_alerts: int | None = None
    crop_code: str | None = None
    expiring_30d_skus: int | None = None
    farm_code: str | None = None
    high_risk_fields: int | None = None
    low_stock_skus: int | None = None
    offline_sensors: int | None = None
    over_budget_count: int | None = None
    pest_cases_90d: int | None = None
    profit_margin_pct: float | None = None
    profit_vnd: float | None = None
    revenue_vnd: float | None = None
    share_pct: float | None = None
    total_cost_vnd: float | None = None
    yield_risk_count: int | None = None


class InsightModel(RecordModel):
    code: str
    evidence: InsightEvidenceModel
    severity: Literal["info", "watch", "warning"]
    summary: str
    title: str


class MonthlyFinancialModel(RecordModel):
    month: str
    revenue_vnd: float
    cost_vnd: float
    profit_vnd: float


class ExecutiveSummaryModel(RecordModel):
    total_revenue_vnd: float
    total_cost_vnd: float
    profit_vnd: float
    profit_margin_pct: float
    harvest_quantity_kg: float
    cultivated_area_ha: float
    active_seasons: int
    risk_alerts: int
    season_risk_alerts: int
    inventory_risk_alerts: int
    crop_health_risk_alerts: int


class FarmScopeSummaryModel(RecordModel):
    total_revenue_vnd: float
    total_cost_vnd: float
    profit_vnd: float
    profit_margin_pct: float
    harvest_quantity_kg: float
    cultivated_area_ha: float
    farm_count: int


class RiskAlertModel(RecordModel):
    season_code: str
    farm_name: str
    field_name: str
    crop_name: str
    status: str
    budget_cost_vnd: float
    actual_cost_vnd: float
    target_yield_kg: float
    actual_yield_kg: float | None
    risk_type: str


class FarmPerformanceModel(RecordModel):
    farm_code: str
    farm_name: str
    cultivated_area_ha: float
    harvested_area_ha: float
    harvest_quantity_kg: float
    total_revenue_vnd: float
    total_cost_vnd: float
    profit_vnd: float
    yield_kg_per_ha: float
    cost_vnd_per_ha: float
    profit_margin_pct: float


class CropProfitabilityModel(RecordModel):
    crop_code: str
    crop_name: str
    operated_area_ha: float
    harvest_quantity_kg: float
    total_revenue_vnd: float
    total_cost_vnd: float
    profit_vnd: float
    profit_margin_pct: float


class InventoryAbcModel(RecordModel):
    material_code: str
    material_name: str
    category: str
    inventory_value_vnd: float
    stock_locations: int
    value_share_pct: float
    cumulative_value_share_pct: float
    abc_class: Literal["A", "B", "C"]


class InventoryAlertModel(RecordModel):
    farm_name: str
    warehouse_code: str
    warehouse_name: str
    material_code: str
    material_name: str
    category: str
    abc_class: Literal["A", "B", "C"]
    stock_quantity: float
    base_unit: str
    alert_type: str
    severity: str
    message: str
    recommended_action: str


class InventoryStatusModel(RecordModel):
    farm_code: str
    farm_name: str
    warehouse_code: str
    warehouse_name: str
    material_code: str
    material_name: str
    category: str
    base_unit: str
    reorder_point: float
    target_stock_level: float
    stock_quantity: float
    average_unit_cost_vnd: float
    inventory_value_vnd: float
    average_daily_usage: float
    days_of_supply: float | None
    nearest_expiry_date: str | None
    days_to_expiry: float | None
    stock_status: str
    recommended_order_quantity: float
    predicted_30d_need: float
    abc_class: Literal["A", "B", "C"]


class InventorySummaryModel(RecordModel):
    total_inventory_value_vnd: float
    material_skus: int
    sku_locations: int
    low_stock_skus: int
    stockout_skus: int
    overstock_skus: int
    expiring_30d_skus: int
    average_days_of_supply: float | None
    critical_alerts: int


class CropHealthAlertModel(RecordModel):
    farm_code: str
    farm_name: str
    field_code: str
    field_name: str
    crop_name: str
    risk_score: float
    risk_status: str
    soil_moisture_pct: float
    soil_ph: float
    rainfall_7d_mm: float
    pest_cases_90d: int
    max_affected_area_pct: float
    sensor_age_days: float
    recommended_action: str


class FieldHealthModel(RecordModel):
    farm_code: str
    farm_name: str
    field_code: str
    field_name: str
    area_ha: float
    latitude: float
    longitude: float
    crop_code: str
    crop_name: str
    temperature_c: float
    air_humidity_pct: float
    soil_moisture_pct: float
    soil_ph: float
    battery_pct: float
    reading_count_7d: int
    last_reading_at: str
    sensor_age_days: float
    pest_cases_90d: int
    max_affected_area_pct: float
    max_mortality_pct: float
    rainfall_7d_mm: float
    risk_score: float
    risk_status: str
    recommended_action: str


class PestIncidentModel(RecordModel):
    week: str
    pest_code: str
    pest_name: str
    case_count: int
    average_affected_area_pct: float
    max_affected_area_pct: float


class CropHealthSummaryModel(RecordModel):
    monitored_fields: int
    readings_7d: int
    average_temperature_c: float | None = None
    average_soil_moisture_pct: float | None = None
    average_soil_ph: float | None = None
    high_risk_fields: int
    watch_fields: int
    offline_sensors: int
    pest_cases_90d: int


class CostBreakdownModel(RecordModel):
    activity_type: str
    material_cost_vnd: float
    labor_cost_vnd: float
    total_cost_vnd: float
    share_pct: float


class CostFarmModel(RecordModel):
    farm_code: str
    farm_name: str
    province: str
    season_count: int
    field_count: int
    season_area_ha: float
    budget_operating_cost_vnd: float
    target_yield_kg: float
    harvest_quantity_kg: float
    revenue_vnd: float
    operating_material_cost_vnd: float
    operating_labor_cost_vnd: float
    operating_total_cost_vnd: float
    operating_profit_vnd: float
    operating_profit_margin_pct: float
    operating_cost_per_ha_vnd: float
    operating_cost_per_kg_vnd: float | None
    budget_variance_vnd: float


class CostMonthlyModel(RecordModel):
    month: str
    operating_material_cost_vnd: float
    operating_labor_cost_vnd: float
    operating_total_cost_vnd: float
    revenue_vnd: float
    operating_profit_vnd: float
    operating_profit_margin_pct: float


class CostSummaryModel(RecordModel):
    season_count: int
    activity_count: int | None = None
    operating_material_cost_vnd: float
    operating_labor_cost_vnd: float
    operating_total_cost_vnd: float
    harvest_quantity_kg: float
    revenue_vnd: float
    operating_profit_vnd: float
    operating_profit_margin_pct: float
    budget_operating_cost_vnd: float
    budget_variance_vnd: float
    operating_cost_per_kg_vnd: float | None


class CostCapabilitiesModel(RecordModel):
    read_only: Literal[True]
    file_export_available: Literal[False]
    monthly_breakdown_available: bool
    activity_breakdown_available: bool


class QualityCheckModel(RecordModel):
    check: str
    failed_rows: int
    severity: str
    table: str
    total_rows: int


class QualityChecksModel(RecordModel):
    after: list[QualityCheckModel]
    before: list[QualityCheckModel]


class QualityScoreModel(RecordModel):
    completeness_pct: float
    freshness_age_days: float
    freshness_pct: float
    uniqueness_pct: float
    validity_pct: float


class QualityScoresModel(RecordModel):
    after: QualityScoreModel
    before: QualityScoreModel


class QualityRemediationModel(RecordModel):
    codes_canonicalized: int
    duplicates_removed: int
    rows_quarantined: int
    units_converted_to_base: int
    units_converted_to_kg: int
