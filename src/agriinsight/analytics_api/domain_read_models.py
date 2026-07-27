from __future__ import annotations

from typing import Any

import pandas as pd

from agriinsight.analytics_api.auth_scope import AuthorizedScope
from agriinsight.analytics_api.models import (
    CropHealthPayload,
    EvidenceSignalModel,
    InventoryPayload,
    PageModel,
)
from agriinsight.analytics_api.response_shaping import json_safe, records
from agriinsight.analytics_snapshot import ArtifactSnapshot


def inventory_payload(
    snapshot: ArtifactSnapshot,
    scope: AuthorizedScope,
    *,
    warehouse_code: str | None,
    limit: int,
    offset: int,
) -> InventoryPayload:
    status = snapshot.csv["inventory_status"]
    status = status[status["warehouse_code"].isin(scope.warehouse_codes)].copy()
    alerts = snapshot.csv["inventory_alerts"]
    alerts = alerts[alerts["warehouse_code"].isin(scope.warehouse_codes)].copy()
    if warehouse_code:
        status = status[status["warehouse_code"] == warehouse_code]
        alerts = alerts[alerts["warehouse_code"] == warehouse_code]
    status = status.sort_values(
        ["warehouse_code", "material_code"], kind="stable"
    )
    page = status.iloc[offset : offset + limit]
    return InventoryPayload(
        abc=records(_inventory_abc(status)),
        alerts=records(alerts.head(100)),
        items=records(page),
        page=PageModel(
            has_more=offset + limit < len(status),
            limit=limit,
            offset=offset,
            total=len(status),
        ),
        summary=json_safe(_inventory_summary(status, alerts)),
    )


def crop_health_payload(
    snapshot: ArtifactSnapshot,
    scope: AuthorizedScope,
    *,
    farm_code: str | None,
    field_code: str | None,
    limit: int,
    offset: int,
) -> tuple[CropHealthPayload, bool]:
    fields = snapshot.csv["field_health_status"]
    fields = fields[fields["farm_code"].isin(scope.farm_codes)].copy()
    alerts = snapshot.csv["crop_health_alerts"]
    alerts = alerts[alerts["farm_code"].isin(scope.farm_codes)].copy()
    if farm_code:
        fields = fields[fields["farm_code"] == farm_code]
        alerts = alerts[alerts["farm_code"] == farm_code]
    if field_code:
        fields = fields[fields["field_code"] == field_code]
        alerts = alerts[alerts["field_code"] == field_code]
    fields = fields.sort_values(
        ["risk_score", "field_code"],
        ascending=[False, True],
        kind="stable",
    )
    page = fields.iloc[offset : offset + limit]
    tenant_wide = (
        scope.farm_tenant_wide
        and farm_code is None
        and field_code is None
    )
    pest = (
        records(snapshot.csv["pest_incidents_weekly"])
        if tenant_wide
        else []
    )
    payload = CropHealthPayload(
        alerts=records(alerts.head(100)),
        evidence_signals=_crop_evidence(fields),
        fields=records(page),
        page=PageModel(
            has_more=offset + limit < len(fields),
            limit=limit,
            offset=offset,
            total=len(fields),
        ),
        pest_incidents_weekly=pest,
        severity=_crop_severity(fields),
        summary=json_safe(_crop_summary(fields)),
    )
    return payload, not tenant_wide


def _inventory_summary(
    status: pd.DataFrame,
    alerts: pd.DataFrame,
) -> dict[str, Any]:
    finite_supply = status["days_of_supply"].dropna()
    return {
        "total_inventory_value_vnd": float(status["inventory_value_vnd"].sum()),
        "material_skus": int(status["material_code"].nunique()),
        "sku_locations": int(len(status)),
        "low_stock_skus": int((status["stock_status"] == "low_stock").sum()),
        "stockout_skus": int((status["stock_status"] == "stockout").sum()),
        "overstock_skus": int((status["stock_status"] == "overstock").sum()),
        "expiring_30d_skus": int(
            status["days_to_expiry"].between(0, 30, inclusive="both").sum()
        ),
        "average_days_of_supply": (
            float(finite_supply.mean()) if not finite_supply.empty else None
        ),
        "critical_alerts": int((alerts["severity"] == "critical").sum()),
    }


def _inventory_abc(status: pd.DataFrame) -> pd.DataFrame:
    if status.empty:
        return pd.DataFrame()
    grouped = (
        status.groupby(
            ["material_code", "material_name", "category"],
            as_index=False,
        )
        .agg(
            inventory_value_vnd=("inventory_value_vnd", "sum"),
            stock_locations=("warehouse_code", "nunique"),
        )
        .sort_values("inventory_value_vnd", ascending=False, kind="stable")
    )
    total = float(grouped["inventory_value_vnd"].sum())
    grouped["value_share_pct"] = (
        grouped["inventory_value_vnd"] / total * 100 if total else 0.0
    )
    grouped["cumulative_value_share_pct"] = grouped["value_share_pct"].cumsum()
    grouped["abc_class"] = grouped["cumulative_value_share_pct"].map(
        lambda value: "A" if value <= 80 else ("B" if value <= 95 else "C")
    )
    return grouped


def _crop_summary(fields: pd.DataFrame) -> dict[str, Any]:
    if fields.empty:
        return {
            "monitored_fields": 0,
            "readings_7d": 0,
            "high_risk_fields": 0,
            "watch_fields": 0,
            "offline_sensors": 0,
            "pest_cases_90d": 0,
        }
    return {
        "monitored_fields": int(fields["field_code"].nunique()),
        "readings_7d": int(fields["reading_count_7d"].sum()),
        "average_temperature_c": float(fields["temperature_c"].mean()),
        "average_soil_moisture_pct": float(fields["soil_moisture_pct"].mean()),
        "average_soil_ph": float(fields["soil_ph"].mean()),
        "high_risk_fields": int((fields["risk_status"] == "high").sum()),
        "watch_fields": int((fields["risk_status"] == "watch").sum()),
        "offline_sensors": int((fields["sensor_age_days"] > 2).sum()),
        "pest_cases_90d": int(fields["pest_cases_90d"].sum()),
    }


def _crop_severity(fields: pd.DataFrame) -> str:
    statuses = set(fields["risk_status"]) if not fields.empty else set()
    if "high" in statuses:
        return "high"
    if "watch" in statuses:
        return "medium"
    return "none"


def _crop_evidence(fields: pd.DataFrame) -> list[EvidenceSignalModel]:
    summary = _crop_summary(fields)
    return [
        EvidenceSignalModel(
            name="monitoredFields",
            value=summary["monitored_fields"],
        ),
        EvidenceSignalModel(
            name="highRiskFields",
            value=summary["high_risk_fields"],
        ),
        EvidenceSignalModel(
            name="offlineSensors",
            value=summary["offline_sensors"],
        ),
    ]
