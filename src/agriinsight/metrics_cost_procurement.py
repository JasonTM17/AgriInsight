from __future__ import annotations

import sqlite3

import pandas as pd


def build_procurement_gold(
    connection: sqlite3.Connection,
) -> dict[str, pd.DataFrame]:
    """Build non-P&L procurement contracts from inbound inventory facts."""

    procurement_detail = pd.read_sql_query(
        """
        SELECT t.transaction_id,
               t.transaction_date,
               substr(t.transaction_date, 1, 7) AS month,
               t.transaction_type,
               f.farm_code,
               f.farm_name,
               w.warehouse_code,
               w.warehouse_name,
               m.material_code,
               m.material_name,
               m.category AS material_category,
               m.base_unit,
               s.supplier_code,
               s.supplier_name,
               s.province AS supplier_province,
               s.quality_rating AS supplier_quality_rating,
               t.quantity_base_unit AS procurement_quantity_base_unit,
               t.unit_cost_base_unit_vnd AS procurement_unit_cost_vnd,
               t.total_amount_vnd AS procurement_spend_vnd,
               t.batch_code,
               t.expiry_date
        FROM fact_inventory_transaction t
        JOIN dim_farm f USING (farm_key)
        JOIN dim_warehouse w USING (warehouse_key)
        JOIN dim_material m USING (material_key)
        LEFT JOIN dim_supplier s USING (supplier_key)
        WHERE t.transaction_type = 'IN'
        ORDER BY t.transaction_date, t.transaction_id
        """,
        connection,
    )

    procurement_summary = pd.read_sql_query(
        """
        SELECT f.farm_code,
               f.farm_name,
               s.supplier_code,
               s.supplier_name,
               s.province AS supplier_province,
               s.quality_rating AS supplier_quality_rating,
               w.warehouse_code,
               w.warehouse_name,
               m.material_code,
               m.material_name,
               m.category AS material_category,
               m.base_unit,
               COUNT(*) AS procurement_transaction_count,
               SUM(t.quantity_base_unit) AS procurement_quantity_base_unit,
               SUM(t.total_amount_vnd) AS procurement_spend_vnd,
               CASE WHEN SUM(t.quantity_base_unit) > 0
                    THEN SUM(t.total_amount_vnd) / SUM(t.quantity_base_unit)
                    ELSE 0 END AS procurement_average_unit_cost_vnd,
               MIN(t.transaction_date) AS first_procurement_date,
               MAX(t.transaction_date) AS last_procurement_date
        FROM fact_inventory_transaction t
        JOIN dim_farm f USING (farm_key)
        JOIN dim_warehouse w USING (warehouse_key)
        JOIN dim_material m USING (material_key)
        LEFT JOIN dim_supplier s USING (supplier_key)
        WHERE t.transaction_type = 'IN'
        GROUP BY f.farm_code,
                 f.farm_name,
                 s.supplier_code,
                 s.supplier_name,
                 s.province,
                 s.quality_rating,
                 w.warehouse_code,
                 w.warehouse_name,
                 m.material_code,
                 m.material_name,
                 m.category,
                 m.base_unit
        ORDER BY COALESCE(s.supplier_code, ''), w.warehouse_code, m.material_code
        """,
        connection,
    )

    return {
        "procurement_summary": procurement_summary,
        "procurement_detail": procurement_detail,
    }
