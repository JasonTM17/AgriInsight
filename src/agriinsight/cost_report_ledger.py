from __future__ import annotations

import pandas as pd

from agriinsight.cost_report_contract import CostReportMetadata


LEDGER_COLUMNS = (
    "export_version",
    "run_id",
    "as_of_date",
    "source_pipeline",
    "filter_hash",
    "lens",
    "record_id",
    "event_date",
    "month",
    "farm_code",
    "farm_name",
    "field_code",
    "field_name",
    "season_code",
    "crop_code",
    "crop_name",
    "activity_type",
    "warehouse_code",
    "warehouse_name",
    "supplier_code",
    "supplier_name",
    "material_code",
    "material_name",
    "quantity",
    "unit",
    "operating_material_cost_vnd",
    "operating_labor_cost_vnd",
    "operating_total_cost_vnd",
    "procurement_unit_cost_vnd",
    "procurement_spend_vnd",
    "notes",
)


def _operating_rows(cost_detail: pd.DataFrame) -> pd.DataFrame:
    result = pd.DataFrame(index=cost_detail.index)
    result["lens"] = "operating"
    result["record_id"] = cost_detail["activity_id"]
    result["event_date"] = cost_detail["occurred_at"]
    for column in (
        "month",
        "farm_code",
        "farm_name",
        "field_code",
        "field_name",
        "season_code",
        "crop_code",
        "crop_name",
        "activity_type",
        "material_name",
        "notes",
    ):
        result[column] = cost_detail[column]
    result["quantity"] = cost_detail["quantity_kg"]
    result["unit"] = "kg"
    for column in (
        "operating_material_cost_vnd",
        "operating_labor_cost_vnd",
        "operating_total_cost_vnd",
    ):
        result[column] = cost_detail[column]
    return result


def _procurement_rows(procurement_detail: pd.DataFrame) -> pd.DataFrame:
    result = pd.DataFrame(index=procurement_detail.index)
    result["lens"] = "procurement"
    result["record_id"] = procurement_detail["transaction_id"]
    result["event_date"] = procurement_detail["transaction_date"]
    for column in (
        "month",
        "farm_code",
        "farm_name",
        "warehouse_code",
        "warehouse_name",
        "supplier_code",
        "supplier_name",
        "material_code",
        "material_name",
    ):
        result[column] = procurement_detail[column]
    result["quantity"] = procurement_detail["procurement_quantity_base_unit"]
    result["unit"] = procurement_detail["base_unit"]
    result["procurement_unit_cost_vnd"] = procurement_detail[
        "procurement_unit_cost_vnd"
    ]
    result["procurement_spend_vnd"] = procurement_detail["procurement_spend_vnd"]
    return result


def build_csv_ledger(
    cost_detail: pd.DataFrame,
    procurement_detail: pd.DataFrame,
    metadata: CostReportMetadata,
) -> pd.DataFrame:
    result = pd.concat(
        [_operating_rows(cost_detail), _procurement_rows(procurement_detail)],
        ignore_index=True,
    )
    for column, value in (
        ("export_version", metadata.export_version),
        ("run_id", metadata.run_id),
        ("as_of_date", metadata.as_of_date),
        ("source_pipeline", metadata.source_pipeline),
        ("filter_hash", metadata.filter_hash),
    ):
        result[column] = value
    return result.reindex(columns=LEDGER_COLUMNS).sort_values(
        ["lens", "event_date", "record_id"], kind="stable", ignore_index=True
    )
