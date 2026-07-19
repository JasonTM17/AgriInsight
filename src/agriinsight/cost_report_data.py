from __future__ import annotations

from typing import Mapping

import pandas as pd

from agriinsight.cost_report_contract import (
    MAX_DETAIL_ROWS,
    CostReportMetadata,
    CostReportRequest,
    PreparedCostReport,
    ReportValidationError,
)
from agriinsight.cost_report_ledger import build_csv_ledger


def _source_frame(gold: Mapping[str, pd.DataFrame], name: str) -> pd.DataFrame:
    try:
        return gold[name]
    except KeyError as error:
        raise ReportValidationError(f"Missing Gold dataset: {name}") from error


def _filter_details(
    gold: Mapping[str, pd.DataFrame], request: CostReportRequest
) -> tuple[pd.DataFrame, pd.DataFrame]:
    cost = _source_frame(gold, "cost_activity_detail")
    procurement = _source_frame(gold, "procurement_detail")
    cost_mask = pd.Series(request.scope != "procurement", index=cost.index, dtype=bool)
    procurement_mask = pd.Series(
        request.scope != "operating", index=procurement.index, dtype=bool
    )
    if request.farm:
        cost_mask &= cost["farm_code"].eq(request.farm)
        procurement_mask &= procurement["farm_code"].eq(request.farm)
    if request.crop:
        cost_mask &= cost["crop_code"].eq(request.crop)
    if request.season:
        cost_mask &= cost["season_code"].eq(request.season)
    if request.activity:
        cost_mask &= cost["activity_type"].eq(request.activity)
    if request.supplier:
        procurement_mask &= procurement["supplier_code"].eq(request.supplier)
    if request.month_from:
        cost_mask &= cost["month"].ge(request.month_from)
        procurement_mask &= procurement["month"].ge(request.month_from)
    if request.month_to:
        cost_mask &= cost["month"].le(request.month_to)
        procurement_mask &= procurement["month"].le(request.month_to)
    _validate_detail_counts(int(cost_mask.sum()), int(procurement_mask.sum()))
    return cost.loc[cost_mask], procurement.loc[procurement_mask]


def _validate_detail_counts(cost_count: int, procurement_count: int) -> None:
    if cost_count > MAX_DETAIL_ROWS or procurement_count > MAX_DETAIL_ROWS:
        raise ReportValidationError(f"Each detail table is limited to {MAX_DETAIL_ROWS:,} rows")
    if cost_count + procurement_count > MAX_DETAIL_ROWS:
        raise ReportValidationError(f"The complete detail bundle is limited to {MAX_DETAIL_ROWS:,} rows")
    if cost_count + procurement_count == 0:
        raise ReportValidationError("The validated filters produced an empty report")


def _sum(frame: pd.DataFrame, column: str) -> float:
    return float(frame[column].sum()) if not frame.empty else 0.0


def _summary(cost: pd.DataFrame, procurement: pd.DataFrame) -> pd.DataFrame:
    return pd.DataFrame(
        [
            {
                "operating_activity_count": len(cost),
                "operating_material_cost_vnd": _sum(cost, "operating_material_cost_vnd"),
                "operating_labor_cost_vnd": _sum(cost, "operating_labor_cost_vnd"),
                "operating_total_cost_vnd": _sum(cost, "operating_total_cost_vnd"),
                "procurement_transaction_count": len(procurement),
                "procurement_quantity_base_unit": _sum(
                    procurement, "procurement_quantity_base_unit"
                ),
                "procurement_spend_vnd": _sum(procurement, "procurement_spend_vnd"),
            }
        ]
    )


def _monthly(cost: pd.DataFrame, procurement: pd.DataFrame) -> pd.DataFrame:
    operating = (
        cost.groupby("month", as_index=False, sort=True)
        .agg(
            operating_material_cost_vnd=("operating_material_cost_vnd", "sum"),
            operating_labor_cost_vnd=("operating_labor_cost_vnd", "sum"),
            operating_total_cost_vnd=("operating_total_cost_vnd", "sum"),
        )
        if not cost.empty
        else pd.DataFrame(columns=("month", "operating_material_cost_vnd", "operating_labor_cost_vnd", "operating_total_cost_vnd"))
    )
    purchasing = (
        procurement.groupby("month", as_index=False, sort=True)
        .agg(
            procurement_quantity_base_unit=("procurement_quantity_base_unit", "sum"),
            procurement_spend_vnd=("procurement_spend_vnd", "sum"),
        )
        if not procurement.empty
        else pd.DataFrame(columns=("month", "procurement_quantity_base_unit", "procurement_spend_vnd"))
    )
    monthly = operating.merge(
        purchasing, on="month", how="outer", validate="one_to_one"
    )
    numeric_columns = monthly.columns.difference(["month"])
    for column in numeric_columns:
        monthly[column] = pd.to_numeric(monthly[column], errors="raise").fillna(0.0)
    return monthly.sort_values("month", kind="stable", ignore_index=True)


def _checks(summary: pd.DataFrame, cost: pd.DataFrame, procurement: pd.DataFrame) -> pd.DataFrame:
    values = summary.iloc[0]
    checks = (
        (
            "Operating components tie",
            values["operating_total_cost_vnd"]
            - values["operating_material_cost_vnd"]
            - values["operating_labor_cost_vnd"],
            "Cost Detail",
            "Total must equal material plus labor.",
        ),
        (
            "Operating summary ties to detail",
            values["operating_total_cost_vnd"] - _sum(cost, "operating_total_cost_vnd"),
            "Summary",
            "Summary is derived only from filtered activity rows.",
        ),
        (
            "Procurement summary ties to detail",
            values["procurement_spend_vnd"] - _sum(procurement, "procurement_spend_vnd"),
            "Summary",
            "Procurement includes inbound transactions only.",
        ),
    )
    return pd.DataFrame(
        [
            {
                "check_name": name,
                "delta_vnd": float(delta),
                "tolerance_vnd": 0.01,
                "status": "PASS" if abs(float(delta)) <= 0.01 else "FAIL",
                "where_to_fix": location,
                "notes": notes,
            }
            for name, delta, location, notes in checks
        ]
    )


def _metadata(
    request: CostReportRequest,
    metadata: CostReportMetadata,
    cost: pd.DataFrame,
    procurement: pd.DataFrame,
) -> pd.DataFrame:
    entries = {
        "export_version": metadata.export_version,
        "run_id": metadata.run_id,
        "as_of_date": metadata.as_of_date,
        "source_pipeline": metadata.source_pipeline,
        "filter_hash": metadata.filter_hash,
        "operating_detail_rows": len(cost),
        "procurement_detail_rows": len(procurement),
        **{f"filter_{key}": value for key, value in request.canonical_dict().items()},
    }
    return pd.DataFrame(
        [{"item": key, "value": "" if value is None else str(value)} for key, value in entries.items()]
    )


def prepare_cost_report(
    gold: Mapping[str, pd.DataFrame],
    request: CostReportRequest,
    metadata: CostReportMetadata,
) -> PreparedCostReport:
    cost, procurement = _filter_details(gold, request)
    cost = cost.sort_values(
        ["farm_code", "season_code", "field_code", "occurred_at", "activity_id"],
        kind="stable",
        ignore_index=True,
    )
    procurement = procurement.sort_values(
        ["transaction_date", "transaction_id"], kind="stable", ignore_index=True
    )
    summary = _summary(cost, procurement)
    return PreparedCostReport(
        summary=summary,
        monthly=_monthly(cost, procurement),
        cost_detail=cost,
        procurement_detail=procurement,
        checks=_checks(summary, cost, procurement),
        metadata=_metadata(request, metadata, cost, procurement),
        csv_ledger=build_csv_ledger(cost, procurement, metadata),
    )
