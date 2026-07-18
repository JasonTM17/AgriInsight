from __future__ import annotations

import sqlite3
from datetime import date
from pathlib import Path

import pandas as pd

from agriinsight.metrics_crop_health import build_crop_health_gold
from agriinsight.metrics_cost_analysis import build_cost_analysis_gold
from agriinsight.metrics_inventory import build_inventory_gold


def _query(connection: sqlite3.Connection, sql: str) -> pd.DataFrame:
    return pd.read_sql_query(sql, connection)


def build_gold_datasets(db_path: Path) -> dict[str, pd.DataFrame]:
    """Materialize dashboard-ready contracts from the warehouse."""

    connection = sqlite3.connect(db_path)
    try:
        revenue = float(
            connection.execute("SELECT COALESCE(SUM(revenue_vnd), 0) FROM fact_harvest").fetchone()[0]
        )
        cost = float(
            connection.execute(
                "SELECT COALESCE(SUM(total_cost_vnd), 0) FROM fact_crop_activity"
            ).fetchone()[0]
        )
        harvest_quantity = float(
            connection.execute(
                "SELECT COALESCE(SUM(harvest_quantity_kg), 0) FROM fact_harvest"
            ).fetchone()[0]
        )
        cultivated_area = float(
            connection.execute("SELECT COALESCE(SUM(area_ha), 0) FROM dim_field").fetchone()[0]
        )
        active_seasons = int(
            connection.execute(
                "SELECT COUNT(*) FROM dim_season WHERE status = 'active'"
            ).fetchone()[0]
        )
        risk_alerts = int(
            connection.execute(
                """
                WITH season_cost AS (
                    SELECT season_key, SUM(total_cost_vnd) AS actual_cost
                    FROM fact_crop_activity
                    GROUP BY season_key
                ),
                season_harvest AS (
                    SELECT season_key, SUM(harvest_quantity_kg) AS actual_yield
                    FROM fact_harvest
                    GROUP BY season_key
                )
                SELECT COUNT(*)
                FROM dim_season s
                LEFT JOIN season_cost c USING (season_key)
                LEFT JOIN season_harvest h USING (season_key)
                WHERE COALESCE(c.actual_cost, 0) > s.budget_cost_vnd
                   OR (s.status = 'completed' AND COALESCE(h.actual_yield, 0) < s.target_yield_kg * 0.85)
                """
            ).fetchone()[0]
        )
        profit = revenue - cost
        executive_summary = pd.DataFrame(
            [
                {
                    "total_revenue_vnd": revenue,
                    "total_cost_vnd": cost,
                    "profit_vnd": profit,
                    "profit_margin_pct": round(100 * profit / revenue, 2) if revenue else 0.0,
                    "harvest_quantity_kg": harvest_quantity,
                    "cultivated_area_ha": cultivated_area,
                    "active_seasons": active_seasons,
                    "risk_alerts": risk_alerts,
                }
            ]
        )

        monthly_financials = _query(
            connection,
            """
            WITH monthly_cost AS (
                SELECT substr(d.full_date, 1, 7) AS month, SUM(a.total_cost_vnd) AS cost_vnd
                FROM fact_crop_activity a
                JOIN dim_date d USING (date_key)
                GROUP BY month
            ),
            monthly_revenue AS (
                SELECT substr(d.full_date, 1, 7) AS month, SUM(h.revenue_vnd) AS revenue_vnd
                FROM fact_harvest h
                JOIN dim_date d USING (date_key)
                GROUP BY month
            ),
            months AS (
                SELECT month FROM monthly_cost
                UNION
                SELECT month FROM monthly_revenue
            )
            SELECT m.month,
                   COALESCE(r.revenue_vnd, 0) AS revenue_vnd,
                   COALESCE(c.cost_vnd, 0) AS cost_vnd,
                   COALESCE(r.revenue_vnd, 0) - COALESCE(c.cost_vnd, 0) AS profit_vnd
            FROM months m
            LEFT JOIN monthly_cost c USING (month)
            LEFT JOIN monthly_revenue r USING (month)
            ORDER BY m.month
            """,
        )

        farm_performance = _query(
            connection,
            """
            WITH farm_area AS (
                SELECT farm_key, SUM(area_ha) AS cultivated_area_ha
                FROM dim_field
                GROUP BY farm_key
            ),
            season_area AS (
                SELECT s.farm_key, SUM(f.area_ha) AS operated_area_ha
                FROM dim_season s
                JOIN dim_field f USING (field_key)
                GROUP BY s.farm_key
            ),
            farm_cost AS (
                SELECT farm_key, SUM(total_cost_vnd) AS total_cost_vnd
                FROM fact_crop_activity
                GROUP BY farm_key
            ),
            farm_harvest AS (
                SELECT h.farm_key,
                       SUM(h.harvest_quantity_kg) AS harvest_quantity_kg,
                       SUM(h.revenue_vnd) AS total_revenue_vnd,
                       SUM(f.area_ha) AS harvested_area_ha
                FROM fact_harvest h
                JOIN dim_field f USING (field_key)
                GROUP BY h.farm_key
            )
            SELECT f.farm_code,
                   f.farm_name,
                   a.cultivated_area_ha,
                   COALESCE(h.harvested_area_ha, 0) AS harvested_area_ha,
                   COALESCE(h.harvest_quantity_kg, 0) AS harvest_quantity_kg,
                   COALESCE(h.total_revenue_vnd, 0) AS total_revenue_vnd,
                   COALESCE(c.total_cost_vnd, 0) AS total_cost_vnd,
                   COALESCE(h.total_revenue_vnd, 0) - COALESCE(c.total_cost_vnd, 0) AS profit_vnd,
                   CASE WHEN COALESCE(h.harvested_area_ha, 0) > 0
                        THEN h.harvest_quantity_kg / h.harvested_area_ha ELSE 0 END AS yield_kg_per_ha,
                   CASE WHEN COALESCE(sa.operated_area_ha, 0) > 0
                        THEN c.total_cost_vnd / sa.operated_area_ha ELSE 0 END AS cost_vnd_per_ha,
                   CASE WHEN COALESCE(h.total_revenue_vnd, 0) > 0
                        THEN 100.0 * (h.total_revenue_vnd - COALESCE(c.total_cost_vnd, 0)) / h.total_revenue_vnd
                        ELSE 0 END AS profit_margin_pct
            FROM dim_farm f
            JOIN farm_area a USING (farm_key)
            JOIN season_area sa USING (farm_key)
            LEFT JOIN farm_cost c USING (farm_key)
            LEFT JOIN farm_harvest h USING (farm_key)
            ORDER BY profit_vnd DESC
            """,
        )

        crop_profitability = _query(
            connection,
            """
            WITH crop_area AS (
                SELECT s.crop_key, SUM(f.area_ha) AS operated_area_ha
                FROM dim_season s
                JOIN dim_field f USING (field_key)
                GROUP BY s.crop_key
            ),
            crop_cost AS (
                SELECT crop_key, SUM(total_cost_vnd) AS total_cost_vnd
                FROM fact_crop_activity
                GROUP BY crop_key
            ),
            crop_harvest AS (
                SELECT crop_key,
                       SUM(harvest_quantity_kg) AS harvest_quantity_kg,
                       SUM(revenue_vnd) AS total_revenue_vnd
                FROM fact_harvest
                GROUP BY crop_key
            )
            SELECT c.crop_code,
                   c.crop_name,
                   a.operated_area_ha,
                   COALESCE(h.harvest_quantity_kg, 0) AS harvest_quantity_kg,
                   COALESCE(h.total_revenue_vnd, 0) AS total_revenue_vnd,
                   COALESCE(cost.total_cost_vnd, 0) AS total_cost_vnd,
                   COALESCE(h.total_revenue_vnd, 0) - COALESCE(cost.total_cost_vnd, 0) AS profit_vnd,
                   CASE WHEN COALESCE(h.total_revenue_vnd, 0) > 0
                        THEN 100.0 * (h.total_revenue_vnd - COALESCE(cost.total_cost_vnd, 0)) / h.total_revenue_vnd
                        ELSE 0 END AS profit_margin_pct
            FROM dim_crop c
            JOIN crop_area a USING (crop_key)
            LEFT JOIN crop_cost cost USING (crop_key)
            LEFT JOIN crop_harvest h USING (crop_key)
            ORDER BY profit_vnd DESC
            """,
        )

        cost_breakdown = _query(
            connection,
            """
            SELECT t.activity_type,
                   SUM(a.material_cost_vnd) AS material_cost_vnd,
                   SUM(a.labor_cost_vnd) AS labor_cost_vnd,
                   SUM(a.total_cost_vnd) AS total_cost_vnd,
                   100.0 * SUM(a.total_cost_vnd)
                       / NULLIF((SELECT SUM(total_cost_vnd) FROM fact_crop_activity), 0) AS share_pct
            FROM fact_crop_activity a
            JOIN dim_activity_type t USING (activity_type_key)
            GROUP BY t.activity_type
            ORDER BY total_cost_vnd DESC
            """,
        )

        risk_details = _query(
            connection,
            """
            WITH season_cost AS (
                SELECT season_key, SUM(total_cost_vnd) AS actual_cost_vnd
                FROM fact_crop_activity
                GROUP BY season_key
            ),
            season_harvest AS (
                SELECT season_key, SUM(harvest_quantity_kg) AS actual_yield_kg
                FROM fact_harvest
                GROUP BY season_key
            )
            SELECT s.season_code,
                   f.farm_name,
                   fi.field_name,
                   c.crop_name,
                   s.status,
                   s.budget_cost_vnd,
                   COALESCE(sc.actual_cost_vnd, 0) AS actual_cost_vnd,
                   s.target_yield_kg,
                   COALESCE(sh.actual_yield_kg, 0) AS actual_yield_kg,
                   CASE
                       WHEN COALESCE(sc.actual_cost_vnd, 0) > s.budget_cost_vnd THEN 'over_budget'
                       WHEN s.status = 'completed' AND COALESCE(sh.actual_yield_kg, 0) < s.target_yield_kg * 0.85
                           THEN 'yield_below_target'
                   END AS risk_type
            FROM dim_season s
            JOIN dim_farm f USING (farm_key)
            JOIN dim_field fi USING (field_key)
            JOIN dim_crop c USING (crop_key)
            LEFT JOIN season_cost sc USING (season_key)
            LEFT JOIN season_harvest sh USING (season_key)
            WHERE COALESCE(sc.actual_cost_vnd, 0) > s.budget_cost_vnd
               OR (s.status = 'completed' AND COALESCE(sh.actual_yield_kg, 0) < s.target_yield_kg * 0.85)
            ORDER BY f.farm_name, s.season_code
            """,
        )
        as_of_date = date.fromisoformat(
            connection.execute("SELECT as_of_date FROM etl_run LIMIT 1").fetchone()[0]
        )
        inventory_gold = build_inventory_gold(connection, as_of_date)
        crop_health_gold = build_crop_health_gold(connection, as_of_date)
        cost_analysis_gold = build_cost_analysis_gold(connection)

        inventory_risk_count = int(
            inventory_gold["inventory_alerts"]["severity"].isin(("critical", "warning")).sum()
        )
        crop_health_risk_count = int(
            (crop_health_gold["crop_health_alerts"]["risk_status"] == "high").sum()
        )
        executive_summary["season_risk_alerts"] = len(risk_details)
        executive_summary["inventory_risk_alerts"] = inventory_risk_count
        executive_summary["crop_health_risk_alerts"] = crop_health_risk_count
        executive_summary["risk_alerts"] = (
            len(risk_details) + inventory_risk_count + crop_health_risk_count
        )
    finally:
        connection.close()

    return {
        "executive_summary": executive_summary,
        "monthly_financials": monthly_financials,
        "farm_performance": farm_performance,
        "crop_profitability": crop_profitability,
        "cost_breakdown": cost_breakdown,
        "risk_alerts": risk_details,
        **inventory_gold,
        **crop_health_gold,
        **cost_analysis_gold,
    }
