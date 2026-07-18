from __future__ import annotations

import sqlite3
from datetime import date

import pandas as pd


def _inventory_alerts(status: pd.DataFrame) -> pd.DataFrame:
    alerts: list[dict[str, object]] = []
    for row in status.itertuples(index=False):
        common = {
            "farm_name": row.farm_name,
            "warehouse_code": row.warehouse_code,
            "warehouse_name": row.warehouse_name,
            "material_code": row.material_code,
            "material_name": row.material_name,
            "category": row.category,
            "abc_class": row.abc_class,
            "stock_quantity": row.stock_quantity,
            "base_unit": row.base_unit,
        }
        if row.stock_status == "stockout":
            alerts.append(
                {
                    **common,
                    "alert_type": "stockout",
                    "severity": "critical",
                    "message": "Tồn kho âm hoặc bằng 0",
                    "recommended_action": (
                        f"Đặt tối thiểu {row.recommended_order_quantity:,.1f} {row.base_unit} ngay."
                    ),
                }
            )
        elif row.stock_status == "low_stock":
            severity = "critical" if row.abc_class == "A" else "warning"
            alerts.append(
                {
                    **common,
                    "alert_type": "low_stock",
                    "severity": severity,
                    "message": "Tồn kho dưới điểm đặt hàng",
                    "recommended_action": (
                        f"Đề nghị nhập {row.recommended_order_quantity:,.1f} {row.base_unit}."
                    ),
                }
            )
        elif row.stock_status == "overstock":
            alerts.append(
                {
                    **common,
                    "alert_type": "overstock",
                    "severity": "watch",
                    "message": "Tồn kho cao hơn 150% mức mục tiêu",
                    "recommended_action": "Tạm hoãn đơn nhập tiếp theo và điều chuyển nội bộ.",
                }
            )
        if pd.notna(row.days_to_expiry) and 0 <= float(row.days_to_expiry) <= 30:
            alerts.append(
                {
                    **common,
                    "alert_type": "expiring_soon",
                    "severity": "warning",
                    "message": f"Lô gần nhất hết hạn trong {int(row.days_to_expiry)} ngày",
                    "recommended_action": "Ưu tiên xuất theo FEFO và kiểm tra chất lượng lô.",
                }
            )
    columns = (
        "farm_name",
        "warehouse_code",
        "warehouse_name",
        "material_code",
        "material_name",
        "category",
        "abc_class",
        "stock_quantity",
        "base_unit",
        "alert_type",
        "severity",
        "message",
        "recommended_action",
    )
    return pd.DataFrame(alerts, columns=columns)


def build_inventory_gold(
    connection: sqlite3.Connection, as_of_date: date
) -> dict[str, pd.DataFrame]:
    as_of = as_of_date.isoformat()
    inventory_status = pd.read_sql_query(
        """
        WITH movement AS (
            SELECT warehouse_key,
                   material_key,
                   SUM(CASE WHEN transaction_type = 'IN'
                            THEN quantity_base_unit ELSE -quantity_base_unit END) AS stock_quantity
            FROM fact_inventory_transaction
            WHERE transaction_date <= ?
            GROUP BY warehouse_key, material_key
        ),
        inbound_cost AS (
            SELECT warehouse_key,
                   material_key,
                   SUM(quantity_base_unit * unit_cost_base_unit_vnd)
                       / NULLIF(SUM(quantity_base_unit), 0) AS average_unit_cost_vnd
            FROM fact_inventory_transaction
            WHERE transaction_type = 'IN' AND transaction_date <= ?
            GROUP BY warehouse_key, material_key
        ),
        usage_30d AS (
            SELECT warehouse_key,
                   material_key,
                   SUM(quantity_base_unit) / 30.0 AS average_daily_usage
            FROM fact_inventory_transaction
            WHERE transaction_type = 'OUT'
              AND transaction_date > date(?, '-30 day')
              AND transaction_date <= ?
            GROUP BY warehouse_key, material_key
        ),
        nearest_expiry AS (
            SELECT warehouse_key,
                   material_key,
                   MIN(expiry_date) AS nearest_expiry_date
            FROM fact_inventory_transaction
            WHERE transaction_type = 'IN'
              AND expiry_date IS NOT NULL
              AND date(expiry_date) >= date(?)
            GROUP BY warehouse_key, material_key
        )
        SELECT f.farm_code,
               f.farm_name,
               w.warehouse_code,
               w.warehouse_name,
               m.material_code,
               m.material_name,
               m.category,
               m.base_unit,
               m.reorder_point,
               m.target_stock_level,
               COALESCE(mv.stock_quantity, 0) AS stock_quantity,
               COALESCE(ic.average_unit_cost_vnd, m.reference_unit_cost_vnd) AS average_unit_cost_vnd,
               MAX(COALESCE(mv.stock_quantity, 0), 0)
                   * COALESCE(ic.average_unit_cost_vnd, m.reference_unit_cost_vnd)
                   AS inventory_value_vnd,
               COALESCE(u.average_daily_usage, 0) AS average_daily_usage,
               CASE WHEN COALESCE(u.average_daily_usage, 0) > 0
                         AND COALESCE(mv.stock_quantity, 0) > 0
                    THEN mv.stock_quantity / u.average_daily_usage END AS days_of_supply,
               ne.nearest_expiry_date,
               CASE WHEN ne.nearest_expiry_date IS NOT NULL
                    THEN CAST(julianday(ne.nearest_expiry_date) - julianday(?) AS INTEGER) END
                    AS days_to_expiry,
               CASE
                   WHEN COALESCE(mv.stock_quantity, 0) <= 0 THEN 'stockout'
                   WHEN mv.stock_quantity <= m.reorder_point THEN 'low_stock'
                   WHEN mv.stock_quantity > m.target_stock_level * 1.5 THEN 'overstock'
                   ELSE 'healthy'
               END AS stock_status,
               MAX(m.target_stock_level - COALESCE(mv.stock_quantity, 0), 0)
                   AS recommended_order_quantity,
               COALESCE(u.average_daily_usage, 0) * 30 AS predicted_30d_need
        FROM dim_warehouse w
        JOIN dim_farm f USING (farm_key)
        CROSS JOIN dim_material m
        LEFT JOIN movement mv
          ON mv.warehouse_key = w.warehouse_key AND mv.material_key = m.material_key
        LEFT JOIN inbound_cost ic
          ON ic.warehouse_key = w.warehouse_key AND ic.material_key = m.material_key
        LEFT JOIN usage_30d u
          ON u.warehouse_key = w.warehouse_key AND u.material_key = m.material_key
        LEFT JOIN nearest_expiry ne
          ON ne.warehouse_key = w.warehouse_key AND ne.material_key = m.material_key
        ORDER BY f.farm_name, m.category, m.material_name
        """,
        connection,
        params=(as_of, as_of, as_of, as_of, as_of, as_of),
    )

    material_value = (
        inventory_status.groupby(["material_code", "material_name", "category"], as_index=False)
        .agg(
            inventory_value_vnd=("inventory_value_vnd", "sum"),
            stock_locations=("warehouse_code", "count"),
        )
        .sort_values("inventory_value_vnd", ascending=False)
        .reset_index(drop=True)
    )
    total_value = float(material_value["inventory_value_vnd"].sum())
    if total_value > 0:
        material_value["cumulative_share_before"] = (
            material_value["inventory_value_vnd"].cumsum()
            - material_value["inventory_value_vnd"]
        ) / total_value
        material_value["abc_class"] = material_value["cumulative_share_before"].map(
            lambda share: "A" if share < 0.80 else "B" if share < 0.95 else "C"
        )
        material_value["value_share_pct"] = (
            100 * material_value["inventory_value_vnd"] / total_value
        )
        material_value["cumulative_value_share_pct"] = material_value[
            "value_share_pct"
        ].cumsum()
    else:
        material_value["abc_class"] = "C"
        material_value["value_share_pct"] = 0.0
        material_value["cumulative_value_share_pct"] = 0.0
    material_value = material_value.drop(columns=["cumulative_share_before"], errors="ignore")
    inventory_status = inventory_status.merge(
        material_value[["material_code", "abc_class"]],
        on="material_code",
        how="left",
        validate="many_to_one",
    )

    alerts = _inventory_alerts(inventory_status)
    finite_supply = inventory_status["days_of_supply"].dropna()
    inventory_summary = pd.DataFrame(
        [
            {
                "total_inventory_value_vnd": total_value,
                "material_skus": int(inventory_status["material_code"].nunique()),
                "sku_locations": len(inventory_status),
                "low_stock_skus": int(
                    inventory_status["stock_status"].isin(("low_stock", "stockout")).sum()
                ),
                "stockout_skus": int((inventory_status["stock_status"] == "stockout").sum()),
                "overstock_skus": int((inventory_status["stock_status"] == "overstock").sum()),
                "expiring_30d_skus": int(
                    inventory_status["days_to_expiry"].between(0, 30, inclusive="both").sum()
                ),
                "average_days_of_supply": (
                    round(float(finite_supply.mean()), 2) if not finite_supply.empty else 0.0
                ),
                "critical_alerts": int((alerts["severity"] == "critical").sum()),
            }
        ]
    )

    movements = pd.read_sql_query(
        """
        SELECT substr(transaction_date, 1, 7) AS month,
               transaction_type,
               SUM(quantity_base_unit * unit_cost_base_unit_vnd) AS movement_value_vnd,
               COUNT(*) AS transaction_count
        FROM fact_inventory_transaction
        GROUP BY month, transaction_type
        ORDER BY month, transaction_type
        """,
        connection,
    )

    return {
        "inventory_summary": inventory_summary,
        "inventory_status": inventory_status,
        "inventory_abc": material_value,
        "inventory_movements_monthly": movements,
        "inventory_alerts": alerts,
    }

