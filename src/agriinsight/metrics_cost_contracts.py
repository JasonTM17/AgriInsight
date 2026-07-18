from __future__ import annotations

from dataclasses import dataclass
from types import MappingProxyType
from typing import Mapping

import numpy as np
import pandas as pd
from pandas.api.types import (
    is_integer_dtype,
    is_numeric_dtype,
    is_object_dtype,
    is_string_dtype,
)


def _names(value: str) -> tuple[str, ...]:
    return tuple(value.split())


@dataclass(frozen=True)
class GoldFrameContract:
    """Exact column order and logical pandas types for one Gold frame."""

    columns: tuple[str, ...]
    string_columns: frozenset[str] = frozenset()
    integer_columns: frozenset[str] = frozenset()

    def validate(self, name: str, frame: pd.DataFrame) -> None:
        actual_columns = tuple(frame.columns)
        if actual_columns != self.columns:
            raise ValueError(
                f"{name} columns do not match the Gold contract: "
                f"expected {self.columns}, got {actual_columns}"
            )
        if frame.empty:
            return

        for column in self.string_columns:
            dtype = frame[column].dtype
            if not (is_string_dtype(dtype) or is_object_dtype(dtype)):
                raise TypeError(f"{name}.{column} must be string-compatible")
        for column in self.integer_columns:
            if not is_integer_dtype(frame[column].dtype):
                raise TypeError(f"{name}.{column} must be integer-compatible")

        numeric_columns = set(self.columns) - self.string_columns
        for column in numeric_columns:
            if not is_numeric_dtype(frame[column].dtype):
                raise TypeError(f"{name}.{column} must be numeric")
        if numeric_columns and not np.isfinite(
            frame[sorted(numeric_columns)].to_numpy(dtype=float)
        ).all():
            raise ValueError(f"{name} contains non-finite numeric values")


def _contract(
    columns: str, *, strings: str = "", integers: str = ""
) -> GoldFrameContract:
    return GoldFrameContract(
        _names(columns), frozenset(_names(strings)), frozenset(_names(integers))
    )


_CONTRACTS = {
    "cost_summary": _contract(
        """season_count activity_count operating_material_cost_vnd
        operating_labor_cost_vnd operating_total_cost_vnd harvest_quantity_kg
        revenue_vnd operating_profit_vnd operating_profit_margin_pct
        budget_operating_cost_vnd budget_variance_vnd operating_cost_per_kg_vnd""",
        integers="season_count activity_count",
    ),
    "cost_monthly": _contract(
        """month operating_material_cost_vnd operating_labor_cost_vnd
        operating_total_cost_vnd revenue_vnd operating_profit_vnd
        operating_profit_margin_pct""",
        strings="month",
    ),
    "cost_farm": _contract(
        """farm_code farm_name province season_count field_count season_area_ha
        budget_operating_cost_vnd target_yield_kg harvest_quantity_kg revenue_vnd
        operating_material_cost_vnd operating_labor_cost_vnd operating_total_cost_vnd
        operating_profit_vnd operating_profit_margin_pct operating_cost_per_ha_vnd
        operating_cost_per_kg_vnd budget_variance_vnd""",
        strings="farm_code farm_name province",
        integers="season_count field_count",
    ),
    "cost_season": _contract(
        """farm_code farm_name field_code field_name season_code crop_code crop_name
        season_status start_date expected_harvest_date area_ha budget_operating_cost_vnd
        target_yield_kg harvest_quantity_kg revenue_vnd operating_material_cost_vnd
        operating_labor_cost_vnd operating_total_cost_vnd operating_profit_vnd
        operating_profit_margin_pct operating_cost_per_ha_vnd operating_cost_per_kg_vnd
        budget_variance_vnd budget_variance_pct""",
        strings="""farm_code farm_name field_code field_name season_code crop_code
        crop_name season_status start_date expected_harvest_date""",
    ),
    "cost_activity": _contract(
        """farm_code farm_name field_code field_name season_code crop_code crop_name
        activity_type activity_count operating_quantity_kg operating_labor_hours
        operating_material_cost_vnd operating_labor_cost_vnd operating_total_cost_vnd
        operating_cost_share_pct""",
        strings="""farm_code farm_name field_code field_name season_code crop_code
        crop_name activity_type""",
        integers="activity_count",
    ),
    "cost_activity_detail": _contract(
        """activity_id occurred_at month farm_code farm_name field_code field_name
        season_code crop_code crop_name activity_type material_name quantity_kg labor_hours
        operating_material_cost_vnd operating_labor_cost_vnd operating_total_cost_vnd notes""",
        strings="""activity_id occurred_at month farm_code farm_name field_code field_name
        season_code crop_code crop_name activity_type material_name notes""",
    ),
    "procurement_summary": _contract(
        """farm_code farm_name supplier_code supplier_name supplier_province
        supplier_quality_rating warehouse_code warehouse_name material_code material_name
        material_category base_unit procurement_transaction_count
        procurement_quantity_base_unit procurement_spend_vnd
        procurement_average_unit_cost_vnd first_procurement_date last_procurement_date""",
        strings="""farm_code farm_name supplier_code supplier_name supplier_province
        warehouse_code warehouse_name material_code material_name material_category base_unit
        first_procurement_date last_procurement_date""",
        integers="procurement_transaction_count",
    ),
    "procurement_detail": _contract(
        """transaction_id transaction_date month transaction_type farm_code farm_name
        warehouse_code warehouse_name material_code material_name material_category base_unit
        supplier_code supplier_name supplier_province supplier_quality_rating
        procurement_quantity_base_unit procurement_unit_cost_vnd procurement_spend_vnd
        batch_code expiry_date""",
        strings="""transaction_id transaction_date month transaction_type farm_code farm_name
        warehouse_code warehouse_name material_code material_name material_category base_unit
        supplier_code supplier_name supplier_province batch_code expiry_date""",
    ),
    "cost_reconciliation": _contract(
        """farm_code season_code budget_operating_cost_vnd operating_material_cost_vnd
        operating_labor_cost_vnd operating_total_cost_vnd operating_component_total_vnd
        component_delta_vnd budget_variance_vnd reconciliation_status""",
        strings="farm_code season_code reconciliation_status",
    ),
}

COST_GOLD_CONTRACTS: Mapping[str, GoldFrameContract] = MappingProxyType(_CONTRACTS)


def validate_cost_gold_contracts(frames: Mapping[str, pd.DataFrame]) -> None:
    if set(frames) != set(COST_GOLD_CONTRACTS):
        raise ValueError("Cost Gold dataset keys do not match the published contract")
    for name, contract in COST_GOLD_CONTRACTS.items():
        contract.validate(name, frames[name])
