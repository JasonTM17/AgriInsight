from __future__ import annotations

from typing import Any

import pandas as pd

from agriinsight.analytics_api.auth_scope import AuthorizedScope
from agriinsight.analytics_api.models import (
    CostsPayload,
    DataQualityPayload,
    EvidenceSignalModel,
)
from agriinsight.analytics_api.response_shaping import first_record, json_safe, records
from agriinsight.analytics_snapshot import ArtifactSnapshot


def data_quality_payload(snapshot: ArtifactSnapshot) -> DataQualityPayload:
    document = snapshot.json["quality"]
    return DataQualityPayload(
        checks=json_safe(document.get("checks", {})),
        evidence_signals=_quality_evidence(document),
        remediation_actions=json_safe(document.get("remediation_actions", {})),
        scores=json_safe(document.get("scores", {})),
        severity="none" if document.get("status") == "passed" else "high",
        status=str(document.get("status", "unknown")),
    )


def costs_payload(
    snapshot: ArtifactSnapshot,
    scope: AuthorizedScope,
) -> tuple[CostsPayload, bool]:
    if scope.farm_tenant_wide:
        return (
            CostsPayload(
                breakdown=records(snapshot.csv["cost_breakdown"]),
                capabilities=_cost_capabilities(True),
                farms=records(snapshot.csv["cost_farm"]),
                monthly=records(snapshot.csv["cost_monthly"]),
                summary=first_record(snapshot.csv["cost_summary"]),
            ),
            False,
        )
    farms = snapshot.csv["cost_farm"]
    farms = farms[farms["farm_code"].isin(scope.farm_codes)].copy()
    return (
        CostsPayload(
            breakdown=[],
            capabilities=_cost_capabilities(False),
            farms=records(farms),
            monthly=[],
            summary=json_safe(_cost_summary(farms)),
        ),
        True,
    )


def _quality_evidence(document: dict[str, Any]) -> list[EvidenceSignalModel]:
    scores = document.get("scores", {})
    if not isinstance(scores, dict):
        return []
    return [
        EvidenceSignalModel(name=str(name), value=json_safe(value))
        for name, value in sorted(scores.items())
    ]


def _cost_summary(farms: pd.DataFrame) -> dict[str, Any]:
    revenue = float(farms["revenue_vnd"].sum())
    cost = float(farms["operating_total_cost_vnd"].sum())
    profit = revenue - cost
    harvest = float(farms["harvest_quantity_kg"].sum())
    return {
        "season_count": int(farms["season_count"].sum()),
        "operating_material_cost_vnd": float(
            farms["operating_material_cost_vnd"].sum()
        ),
        "operating_labor_cost_vnd": float(
            farms["operating_labor_cost_vnd"].sum()
        ),
        "operating_total_cost_vnd": cost,
        "harvest_quantity_kg": harvest,
        "revenue_vnd": revenue,
        "operating_profit_vnd": profit,
        "operating_profit_margin_pct": profit / revenue * 100 if revenue else 0.0,
        "budget_operating_cost_vnd": float(
            farms["budget_operating_cost_vnd"].sum()
        ),
        "budget_variance_vnd": float(farms["budget_variance_vnd"].sum()),
        "operating_cost_per_kg_vnd": cost / harvest if harvest else None,
    }


def _cost_capabilities(tenant_wide: bool) -> dict[str, Any]:
    return {
        "readOnly": True,
        "fileExportAvailable": False,
        "monthlyBreakdownAvailable": tenant_wide,
        "activityBreakdownAvailable": tenant_wide,
    }
