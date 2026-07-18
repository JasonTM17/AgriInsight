from __future__ import annotations

import pandas as pd

from agriinsight.domain_transform import DomainTransformResult


VALID_BASE_UNITS = {"kg", "liter", "piece"}
UNIT_ALIASES = {
    "kg": "kg",
    "kilogram": "kg",
    "tonne": "tonne",
    "ton": "tonne",
    "t": "tonne",
    "l": "liter",
    "litre": "liter",
    "liter": "liter",
    "pcs": "piece",
    "pc": "piece",
    "piece": "piece",
}


def _code(series: pd.Series) -> pd.Series:
    return series.astype("string").str.strip().str.upper()


def _reject(frame: pd.DataFrame, mask: pd.Series, reason: str) -> pd.DataFrame:
    rejected = frame.loc[mask].copy()
    rejected["quarantine_reason"] = reason
    return rejected


def _combine(parts: list[pd.DataFrame], columns: list[str]) -> pd.DataFrame:
    populated = [part for part in parts if not part.empty]
    if not populated:
        return pd.DataFrame(columns=[*columns, "quarantine_reason"])
    return pd.concat(populated, ignore_index=True)


def clean_inventory(
    raw: dict[str, pd.DataFrame], valid_farm_codes: set[str]
) -> DomainTransformResult:
    actions = {
        "codes_canonicalized": 0,
        "duplicates_removed": 0,
        "units_converted_to_base": 0,
    }
    quarantine: dict[str, pd.DataFrame] = {}

    warehouses = raw["warehouses"].copy()
    for column in ("warehouse_code", "farm_code"):
        original = warehouses[column].astype("string")
        warehouses[column] = _code(warehouses[column])
        actions["codes_canonicalized"] += int((original != warehouses[column]).sum())
    warehouses["capacity_value_vnd"] = pd.to_numeric(
        warehouses["capacity_value_vnd"], errors="coerce"
    )
    duplicate = warehouses.duplicated("warehouse_code", keep="first")
    invalid = (
        warehouses[["warehouse_code", "farm_code", "warehouse_name"]].isna().any(axis=1)
        | ~warehouses["farm_code"].isin(valid_farm_codes)
        | warehouses["capacity_value_vnd"].isna()
        | (warehouses["capacity_value_vnd"] <= 0)
    )
    quarantine["warehouses"] = _combine(
        [
            _reject(warehouses, duplicate, "duplicate_primary_key"),
            _reject(warehouses, invalid & ~duplicate, "invalid_dimension_reference"),
        ],
        list(raw["warehouses"].columns),
    )
    actions["duplicates_removed"] += int(duplicate.sum())
    warehouses = warehouses.loc[~duplicate & ~invalid].reset_index(drop=True)

    materials = raw["materials"].copy()
    original = materials["material_code"].astype("string")
    materials["material_code"] = _code(materials["material_code"])
    actions["codes_canonicalized"] += int((original != materials["material_code"]).sum())
    materials["base_unit"] = materials["base_unit"].astype("string").str.strip().str.lower()
    numeric_material_columns = (
        "reorder_point",
        "target_stock_level",
        "shelf_life_days",
        "reference_unit_cost_vnd",
    )
    for column in numeric_material_columns:
        materials[column] = pd.to_numeric(materials[column], errors="coerce")
    duplicate = materials.duplicated("material_code", keep="first")
    invalid = (
        materials[["material_code", "material_name", "category", "base_unit"]]
        .isna()
        .any(axis=1)
        | ~materials["base_unit"].isin(VALID_BASE_UNITS)
        | materials[list(numeric_material_columns)].isna().any(axis=1)
        | (materials[list(numeric_material_columns)] <= 0).any(axis=1)
        | (materials["target_stock_level"] <= materials["reorder_point"])
    )
    quarantine["materials"] = _combine(
        [
            _reject(materials, duplicate, "duplicate_primary_key"),
            _reject(materials, invalid & ~duplicate, "invalid_dimension_value"),
        ],
        list(raw["materials"].columns),
    )
    actions["duplicates_removed"] += int(duplicate.sum())
    materials = materials.loc[~duplicate & ~invalid].reset_index(drop=True)

    suppliers = raw["suppliers"].copy()
    original = suppliers["supplier_code"].astype("string")
    suppliers["supplier_code"] = _code(suppliers["supplier_code"])
    actions["codes_canonicalized"] += int((original != suppliers["supplier_code"]).sum())
    suppliers["quality_rating"] = pd.to_numeric(suppliers["quality_rating"], errors="coerce")
    duplicate = suppliers.duplicated("supplier_code", keep="first")
    invalid = (
        suppliers[["supplier_code", "supplier_name", "province"]].isna().any(axis=1)
        | suppliers["quality_rating"].isna()
        | ~suppliers["quality_rating"].between(0, 5)
    )
    quarantine["suppliers"] = _combine(
        [
            _reject(suppliers, duplicate, "duplicate_primary_key"),
            _reject(suppliers, invalid & ~duplicate, "invalid_dimension_value"),
        ],
        list(raw["suppliers"].columns),
    )
    actions["duplicates_removed"] += int(duplicate.sum())
    suppliers = suppliers.loc[~duplicate & ~invalid].reset_index(drop=True)

    transactions = raw["inventory_transactions"].copy()
    for column in ("transaction_id", "warehouse_code", "material_code", "supplier_code"):
        original = transactions[column].astype("string")
        transactions[column] = _code(transactions[column])
        actions["codes_canonicalized"] += int(
            ((original.notna()) & (original != transactions[column])).sum()
        )
    transactions["transaction_type"] = (
        transactions["transaction_type"].astype("string").str.strip().str.upper()
    )
    transactions["transaction_date"] = pd.to_datetime(
        transactions["transaction_date"], errors="coerce"
    )
    transactions["expiry_date"] = pd.to_datetime(transactions["expiry_date"], errors="coerce")
    for column in ("quantity", "unit_cost_vnd", "total_amount_vnd"):
        transactions[column] = pd.to_numeric(transactions[column], errors="coerce")
    transactions["unit"] = (
        transactions["unit"].astype("string").str.strip().str.lower().map(UNIT_ALIASES)
    )
    base_unit_lookup = materials.set_index("material_code")["base_unit"].to_dict()
    transactions["base_unit"] = transactions["material_code"].map(base_unit_lookup)
    tonne_to_kg = transactions["unit"].eq("tonne") & transactions["base_unit"].eq("kg")
    actions["units_converted_to_base"] += int(tonne_to_kg.sum())
    conversion_factor = pd.Series(1.0, index=transactions.index).where(~tonne_to_kg, 1_000.0)
    transactions["quantity_base_unit"] = transactions["quantity"] * conversion_factor
    transactions["unit_cost_base_unit_vnd"] = transactions["unit_cost_vnd"] / conversion_factor
    canonical_unit = transactions["unit"].where(~tonne_to_kg, "kg")

    duplicate = transactions.duplicated("transaction_id", keep="first")
    actions["duplicates_removed"] += int(duplicate.sum())
    expected_amount = (
        transactions["quantity_base_unit"] * transactions["unit_cost_base_unit_vnd"]
    )
    amount_tolerance = expected_amount.abs() * 0.02 + 1_000
    amount_mismatch = (transactions["total_amount_vnd"] - expected_amount).abs() > amount_tolerance
    inbound = transactions["transaction_type"].eq("IN")
    invalid = (
        transactions["transaction_id"].isna()
        | transactions["transaction_date"].isna()
        | ~transactions["warehouse_code"].isin(warehouses["warehouse_code"])
        | ~transactions["material_code"].isin(materials["material_code"])
        | ~transactions["transaction_type"].isin(("IN", "OUT"))
        | canonical_unit.ne(transactions["base_unit"])
        | transactions[["quantity_base_unit", "unit_cost_base_unit_vnd", "total_amount_vnd"]]
        .isna()
        .any(axis=1)
        | (transactions[["quantity_base_unit", "unit_cost_base_unit_vnd", "total_amount_vnd"]] < 0)
        .any(axis=1)
        | (transactions["quantity_base_unit"] == 0)
        | amount_mismatch
        | (inbound & ~transactions["supplier_code"].isin(suppliers["supplier_code"]))
        | (inbound & transactions["expiry_date"].isna())
        | (inbound & (transactions["expiry_date"] <= transactions["transaction_date"]))
    )
    quarantine["inventory_transactions"] = _combine(
        [
            _reject(transactions, duplicate, "duplicate_primary_key"),
            _reject(transactions, invalid & ~duplicate, "invalid_fact_value_or_reference"),
        ],
        list(raw["inventory_transactions"].columns),
    )
    transactions = transactions.loc[~duplicate & ~invalid].copy()
    transactions["transaction_date"] = transactions["transaction_date"].dt.strftime("%Y-%m-%d")
    transactions["expiry_date"] = transactions["expiry_date"].dt.strftime("%Y-%m-%d")
    transactions = transactions.drop(columns=["quantity", "unit", "unit_cost_vnd"]).reset_index(
        drop=True
    )

    return DomainTransformResult(
        silver={
            "warehouses": warehouses,
            "materials": materials,
            "suppliers": suppliers,
            "inventory_transactions": transactions,
        },
        quarantine=quarantine,
        actions=actions,
    )

