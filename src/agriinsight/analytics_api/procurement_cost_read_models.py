from __future__ import annotations

import pandas as pd

from agriinsight.analytics_api.auth_scope import AuthorizedScope
from agriinsight.analytics_api.models import (
    ProcurementCostsPayload,
    ProcurementPageModel,
)
from agriinsight.analytics_api.record_models import (
    ProcurementCostCapabilitiesModel,
    ProcurementCostItemModel,
    ProcurementCostMonthlyModel,
    ProcurementCostSupplierModel,
    ProcurementCostSummaryModel,
)
from agriinsight.analytics_api.response_shaping import records
from agriinsight.analytics_snapshot import ArtifactSnapshot

_DETAIL_COLUMNS = (
    "transaction_id",
    "transaction_date",
    "farm_code",
    "farm_name",
    "warehouse_code",
    "warehouse_name",
    "material_code",
    "material_name",
    "supplier_code",
    "supplier_name",
    "procurement_quantity_base_unit",
    "procurement_unit_cost_vnd",
    "procurement_spend_vnd",
)


def procurement_costs_payload(
    snapshot: ArtifactSnapshot,
    scope: AuthorizedScope,
    *,
    farm_code: str | None,
    month_from: str | None,
    month_to: str | None,
    limit: int,
    offset: int,
) -> tuple[ProcurementCostsPayload, bool]:
    frame = snapshot.csv["procurement_detail"]
    if not scope.farm_tenant_wide:
        frame = frame[frame["farm_code"].isin(scope.farm_codes)]
    if farm_code is not None:
        frame = frame[frame["farm_code"].eq(farm_code)]
    if month_from is not None:
        frame = frame[frame["month"].ge(month_from)]
    if month_to is not None:
        frame = frame[frame["month"].le(month_to)]
    frame = frame.sort_values(
        ["transaction_date", "transaction_id"],
        kind="stable",
        ignore_index=True,
    )

    total = len(frame)
    page = frame.iloc[offset : offset + limit].loc[:, _DETAIL_COLUMNS]
    payload = ProcurementCostsPayload(
        capabilities=ProcurementCostCapabilitiesModel(
            readOnly=True,
            fileExportAvailable=True,
            detailPageAvailable=True,
        ),
        items=[
            ProcurementCostItemModel.model_validate(item)
            for item in records(page)
        ],
        monthly=_monthly(frame),
        page=ProcurementPageModel(
            hasMore=offset + limit < total,
            limit=limit,
            offset=offset,
            total=min(total, 100_000),
        ),
        suppliers=_suppliers(frame),
        summary=_summary(frame),
    )
    return payload, total == 0


def _summary(frame: pd.DataFrame) -> ProcurementCostSummaryModel:
    return ProcurementCostSummaryModel(
        transactionCount=len(frame),
        procurementQuantityBaseUnit=_sum(frame, "procurement_quantity_base_unit"),
        procurementSpendVnd=_sum(frame, "procurement_spend_vnd"),
    )


def _monthly(frame: pd.DataFrame) -> list[ProcurementCostMonthlyModel]:
    if frame.empty:
        return []
    grouped = (
        frame.groupby("month", as_index=False, sort=True)
        .agg(
            transaction_count=("transaction_id", "count"),
            procurement_quantity_base_unit=(
                "procurement_quantity_base_unit",
                "sum",
            ),
            procurement_spend_vnd=("procurement_spend_vnd", "sum"),
        )
    )
    return [
        ProcurementCostMonthlyModel.model_validate(item)
        for item in records(grouped)
    ]


def _suppliers(frame: pd.DataFrame) -> list[ProcurementCostSupplierModel]:
    if frame.empty:
        return []
    grouped = (
        frame.groupby(["supplier_code", "supplier_name"], as_index=False, sort=True)
        .agg(
            transaction_count=("transaction_id", "count"),
            procurement_spend_vnd=("procurement_spend_vnd", "sum"),
        )
        .sort_values(
            ["procurement_spend_vnd", "supplier_code"],
            ascending=[False, True],
            kind="stable",
        )
        .head(20)
    )
    return [
        ProcurementCostSupplierModel.model_validate(item)
        for item in records(grouped)
    ]


def _sum(frame: pd.DataFrame, column: str) -> float:
    return float(frame[column].sum()) if not frame.empty else 0.0


__all__ = ["procurement_costs_payload"]
