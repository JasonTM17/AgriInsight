from __future__ import annotations

from dataclasses import dataclass
from typing import Mapping

import pandas as pd

from agriinsight.cost_report_contract import CostReportRequest, ReportValidationError


@dataclass(frozen=True, slots=True)
class ProcurementDashboardView:
    transaction_count: int
    procurement_quantity_base_unit: float
    procurement_spend_vnd: float
    material_drivers: pd.DataFrame
    detail: pd.DataFrame


def _frame(gold: Mapping[str, pd.DataFrame], name: str) -> pd.DataFrame:
    try:
        return gold[name]
    except KeyError as error:
        raise ReportValidationError(f"Missing Gold dataset: {name}") from error


def _sum(frame: pd.DataFrame, column: str) -> float:
    return float(frame[column].sum()) if not frame.empty else 0.0


def _material_drivers(detail: pd.DataFrame, top_n: int) -> pd.DataFrame:
    if detail.empty:
        return pd.DataFrame(
            columns=(
                "farm_code",
                "farm_name",
                "supplier_code",
                "supplier_name",
                "warehouse_code",
                "warehouse_name",
                "material_code",
                "material_name",
                "base_unit",
                "transaction_count",
                "procurement_quantity_base_unit",
                "procurement_spend_vnd",
            )
        )
    return (
        detail.groupby(
            [
                "farm_code",
                "farm_name",
                "supplier_code",
                "supplier_name",
                "warehouse_code",
                "warehouse_name",
                "material_code",
                "material_name",
                "base_unit",
            ],
            as_index=False,
            sort=True,
        )
        .agg(
            transaction_count=("transaction_id", "count"),
            procurement_quantity_base_unit=("procurement_quantity_base_unit", "sum"),
            procurement_spend_vnd=("procurement_spend_vnd", "sum"),
        )
        .sort_values(
            [
                "procurement_spend_vnd",
                "farm_code",
                "supplier_code",
                "warehouse_code",
                "material_code",
            ],
            ascending=[False, True, True, True, True],
            kind="stable",
            ignore_index=True,
        )
        .head(top_n)
    )


def prepare_procurement_dashboard(
    gold: Mapping[str, pd.DataFrame], request: CostReportRequest
) -> ProcurementDashboardView:
    if request.scope != "procurement":
        raise ReportValidationError("Procurement dashboard requires scope=procurement")
    detail = _frame(gold, "procurement_detail")
    mask = pd.Series(True, index=detail.index, dtype=bool)
    for column, value in (
        ("farm_code", request.farm),
        ("supplier_code", request.supplier),
    ):
        if value:
            mask &= detail[column].eq(value)
    if request.month_from:
        mask &= detail["month"].ge(request.month_from)
    if request.month_to:
        mask &= detail["month"].le(request.month_to)
    filtered = detail.loc[mask].sort_values(
        ["transaction_date", "transaction_id"], kind="stable", ignore_index=True
    )
    return ProcurementDashboardView(
        transaction_count=len(filtered),
        procurement_quantity_base_unit=_sum(
            filtered, "procurement_quantity_base_unit"
        ),
        procurement_spend_vnd=_sum(filtered, "procurement_spend_vnd"),
        material_drivers=_material_drivers(filtered, request.top_n),
        detail=filtered,
    )


__all__ = ["ProcurementDashboardView", "prepare_procurement_dashboard"]
