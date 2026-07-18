from __future__ import annotations

import sqlite3

import pandas as pd

from agriinsight.metrics_cost_contracts import validate_cost_gold_contracts
from agriinsight.metrics_cost_procurement import build_procurement_gold


def _query(connection: sqlite3.Connection, sql: str) -> pd.DataFrame:
    return pd.read_sql_query(sql, connection)


def _cost_summary(connection: sqlite3.Connection) -> pd.DataFrame:
    return _query(
        connection,
        """
        WITH operating AS (
            SELECT COUNT(*) AS activity_count,
                   COALESCE(SUM(material_cost_vnd), 0) AS material_cost_vnd,
                   COALESCE(SUM(labor_cost_vnd), 0) AS labor_cost_vnd,
                   COALESCE(SUM(total_cost_vnd), 0) AS total_cost_vnd
            FROM fact_crop_activity
        ),
        harvest AS (
            SELECT COALESCE(SUM(harvest_quantity_kg), 0) AS harvest_quantity_kg,
                   COALESCE(SUM(revenue_vnd), 0) AS revenue_vnd
            FROM fact_harvest
        ),
        plan AS (
            SELECT COUNT(*) AS season_count,
                   COALESCE(SUM(budget_cost_vnd), 0) AS budget_cost_vnd
            FROM dim_season
        )
        SELECT p.season_count,
               o.activity_count,
               o.material_cost_vnd AS operating_material_cost_vnd,
               o.labor_cost_vnd AS operating_labor_cost_vnd,
               o.total_cost_vnd AS operating_total_cost_vnd,
               h.harvest_quantity_kg,
               h.revenue_vnd,
               h.revenue_vnd - o.total_cost_vnd AS operating_profit_vnd,
               CASE WHEN h.revenue_vnd > 0
                    THEN 100.0 * (h.revenue_vnd - o.total_cost_vnd) / h.revenue_vnd
                    ELSE 0 END AS operating_profit_margin_pct,
               p.budget_cost_vnd AS budget_operating_cost_vnd,
               o.total_cost_vnd - p.budget_cost_vnd AS budget_variance_vnd,
               CASE WHEN h.harvest_quantity_kg > 0
                    THEN o.total_cost_vnd / h.harvest_quantity_kg
                    ELSE 0 END AS operating_cost_per_kg_vnd
        FROM operating o
        CROSS JOIN harvest h
        CROSS JOIN plan p
        """,
    )


def _cost_monthly(connection: sqlite3.Connection) -> pd.DataFrame:
    return _query(
        connection,
        """
        WITH monthly_cost AS (
            SELECT substr(d.full_date, 1, 7) AS month,
                   SUM(a.material_cost_vnd) AS material_cost_vnd,
                   SUM(a.labor_cost_vnd) AS labor_cost_vnd,
                   SUM(a.total_cost_vnd) AS total_cost_vnd
            FROM fact_crop_activity a
            JOIN dim_date d USING (date_key)
            GROUP BY substr(d.full_date, 1, 7)
        ),
        monthly_revenue AS (
            SELECT substr(d.full_date, 1, 7) AS month,
                   SUM(h.revenue_vnd) AS revenue_vnd
            FROM fact_harvest h
            JOIN dim_date d USING (date_key)
            GROUP BY substr(d.full_date, 1, 7)
        ),
        months AS (
            SELECT month FROM monthly_cost
            UNION
            SELECT month FROM monthly_revenue
        )
        SELECT m.month,
               COALESCE(c.material_cost_vnd, 0) AS operating_material_cost_vnd,
               COALESCE(c.labor_cost_vnd, 0) AS operating_labor_cost_vnd,
               COALESCE(c.total_cost_vnd, 0) AS operating_total_cost_vnd,
               COALESCE(r.revenue_vnd, 0) AS revenue_vnd,
               COALESCE(r.revenue_vnd, 0) - COALESCE(c.total_cost_vnd, 0)
                   AS operating_profit_vnd,
               CASE WHEN COALESCE(r.revenue_vnd, 0) > 0
                    THEN 100.0 * (r.revenue_vnd - COALESCE(c.total_cost_vnd, 0))
                        / r.revenue_vnd
                    ELSE 0 END AS operating_profit_margin_pct
        FROM months m
        LEFT JOIN monthly_cost c USING (month)
        LEFT JOIN monthly_revenue r USING (month)
        ORDER BY m.month
        """,
    )


def _cost_farm(connection: sqlite3.Connection) -> pd.DataFrame:
    return _query(
        connection,
        """
        WITH farm_plan AS (
            SELECT s.farm_key,
                   COUNT(*) AS season_count,
                   COUNT(DISTINCT s.field_key) AS field_count,
                   SUM(fi.area_ha) AS season_area_ha,
                   SUM(s.budget_cost_vnd) AS budget_cost_vnd,
                   SUM(s.target_yield_kg) AS target_yield_kg
            FROM dim_season s
            JOIN dim_field fi USING (field_key)
            GROUP BY s.farm_key
        ),
        farm_cost AS (
            SELECT farm_key,
                   SUM(material_cost_vnd) AS material_cost_vnd,
                   SUM(labor_cost_vnd) AS labor_cost_vnd,
                   SUM(total_cost_vnd) AS total_cost_vnd
            FROM fact_crop_activity
            GROUP BY farm_key
        ),
        farm_harvest AS (
            SELECT farm_key,
                   SUM(harvest_quantity_kg) AS harvest_quantity_kg,
                   SUM(revenue_vnd) AS revenue_vnd
            FROM fact_harvest
            GROUP BY farm_key
        )
        SELECT f.farm_code,
               f.farm_name,
               f.province,
               COALESCE(p.season_count, 0) AS season_count,
               COALESCE(p.field_count, 0) AS field_count,
               COALESCE(p.season_area_ha, 0) AS season_area_ha,
               COALESCE(p.budget_cost_vnd, 0) AS budget_operating_cost_vnd,
               COALESCE(p.target_yield_kg, 0) AS target_yield_kg,
               COALESCE(h.harvest_quantity_kg, 0) AS harvest_quantity_kg,
               COALESCE(h.revenue_vnd, 0) AS revenue_vnd,
               COALESCE(c.material_cost_vnd, 0) AS operating_material_cost_vnd,
               COALESCE(c.labor_cost_vnd, 0) AS operating_labor_cost_vnd,
               COALESCE(c.total_cost_vnd, 0) AS operating_total_cost_vnd,
               COALESCE(h.revenue_vnd, 0) - COALESCE(c.total_cost_vnd, 0)
                   AS operating_profit_vnd,
               CASE WHEN COALESCE(h.revenue_vnd, 0) > 0
                    THEN 100.0 * (h.revenue_vnd - COALESCE(c.total_cost_vnd, 0))
                        / h.revenue_vnd
                    ELSE 0 END AS operating_profit_margin_pct,
               CASE WHEN COALESCE(p.season_area_ha, 0) > 0
                    THEN COALESCE(c.total_cost_vnd, 0) / p.season_area_ha
                    ELSE 0 END AS operating_cost_per_ha_vnd,
               CASE WHEN COALESCE(h.harvest_quantity_kg, 0) > 0
                    THEN COALESCE(c.total_cost_vnd, 0) / h.harvest_quantity_kg
                    ELSE 0 END AS operating_cost_per_kg_vnd,
               COALESCE(c.total_cost_vnd, 0) - COALESCE(p.budget_cost_vnd, 0)
                   AS budget_variance_vnd
        FROM dim_farm f
        LEFT JOIN farm_plan p USING (farm_key)
        LEFT JOIN farm_cost c USING (farm_key)
        LEFT JOIN farm_harvest h USING (farm_key)
        ORDER BY f.farm_code
        """,
    )


def _cost_season(connection: sqlite3.Connection) -> pd.DataFrame:
    return _query(
        connection,
        """
        WITH season_cost AS (
            SELECT season_key,
                   SUM(material_cost_vnd) AS material_cost_vnd,
                   SUM(labor_cost_vnd) AS labor_cost_vnd,
                   SUM(total_cost_vnd) AS total_cost_vnd
            FROM fact_crop_activity
            GROUP BY season_key
        ),
        season_harvest AS (
            SELECT season_key,
                   SUM(harvest_quantity_kg) AS harvest_quantity_kg,
                   SUM(revenue_vnd) AS revenue_vnd
            FROM fact_harvest
            GROUP BY season_key
        )
        SELECT f.farm_code,
               f.farm_name,
               fi.field_code,
               fi.field_name,
               s.season_code,
               c.crop_code,
               c.crop_name,
               s.status AS season_status,
               s.start_date,
               s.expected_harvest_date,
               fi.area_ha,
               s.budget_cost_vnd AS budget_operating_cost_vnd,
               s.target_yield_kg,
               COALESCE(h.harvest_quantity_kg, 0) AS harvest_quantity_kg,
               COALESCE(h.revenue_vnd, 0) AS revenue_vnd,
               COALESCE(sc.material_cost_vnd, 0) AS operating_material_cost_vnd,
               COALESCE(sc.labor_cost_vnd, 0) AS operating_labor_cost_vnd,
               COALESCE(sc.total_cost_vnd, 0) AS operating_total_cost_vnd,
               COALESCE(h.revenue_vnd, 0) - COALESCE(sc.total_cost_vnd, 0)
                   AS operating_profit_vnd,
               CASE WHEN COALESCE(h.revenue_vnd, 0) > 0
                    THEN 100.0 * (h.revenue_vnd - COALESCE(sc.total_cost_vnd, 0))
                        / h.revenue_vnd
                    ELSE 0 END AS operating_profit_margin_pct,
               CASE WHEN fi.area_ha > 0
                    THEN COALESCE(sc.total_cost_vnd, 0) / fi.area_ha
                    ELSE 0 END AS operating_cost_per_ha_vnd,
               CASE WHEN COALESCE(h.harvest_quantity_kg, 0) > 0
                    THEN COALESCE(sc.total_cost_vnd, 0) / h.harvest_quantity_kg
                    ELSE 0 END AS operating_cost_per_kg_vnd,
               COALESCE(sc.total_cost_vnd, 0) - s.budget_cost_vnd AS budget_variance_vnd,
               CASE WHEN s.budget_cost_vnd > 0
                    THEN 100.0 * (COALESCE(sc.total_cost_vnd, 0) - s.budget_cost_vnd)
                        / s.budget_cost_vnd
                    ELSE 0 END AS budget_variance_pct
        FROM dim_season s
        JOIN dim_farm f USING (farm_key)
        JOIN dim_field fi USING (field_key)
        JOIN dim_crop c USING (crop_key)
        LEFT JOIN season_cost sc USING (season_key)
        LEFT JOIN season_harvest h USING (season_key)
        ORDER BY f.farm_code, s.season_code
        """,
    )


def _cost_activity(connection: sqlite3.Connection) -> pd.DataFrame:
    return _query(
        connection,
        """
        WITH activity_aggregate AS (
            SELECT a.farm_key,
                   a.field_key,
                   a.season_key,
                   a.crop_key,
                   a.activity_type_key,
                   COUNT(*) AS activity_count,
                   SUM(a.quantity_kg) AS quantity_kg,
                   SUM(a.labor_hours) AS labor_hours,
                   SUM(a.material_cost_vnd) AS material_cost_vnd,
                   SUM(a.labor_cost_vnd) AS labor_cost_vnd,
                   SUM(a.total_cost_vnd) AS total_cost_vnd
            FROM fact_crop_activity a
            GROUP BY a.farm_key,
                     a.field_key,
                     a.season_key,
                     a.crop_key,
                     a.activity_type_key
        ),
        season_cost AS (
            SELECT season_key, SUM(total_cost_vnd) AS total_cost_vnd
            FROM fact_crop_activity
            GROUP BY season_key
        )
        SELECT f.farm_code,
               f.farm_name,
               fi.field_code,
               fi.field_name,
               s.season_code,
               c.crop_code,
               c.crop_name,
               t.activity_type,
               a.activity_count,
               a.quantity_kg AS operating_quantity_kg,
               a.labor_hours AS operating_labor_hours,
               a.material_cost_vnd AS operating_material_cost_vnd,
               a.labor_cost_vnd AS operating_labor_cost_vnd,
               a.total_cost_vnd AS operating_total_cost_vnd,
               CASE WHEN sc.total_cost_vnd > 0
                    THEN 100.0 * a.total_cost_vnd / sc.total_cost_vnd
                    ELSE 0 END AS operating_cost_share_pct
        FROM activity_aggregate a
        JOIN dim_farm f USING (farm_key)
        JOIN dim_field fi USING (field_key)
        JOIN dim_season s USING (season_key)
        JOIN dim_crop c USING (crop_key)
        JOIN dim_activity_type t USING (activity_type_key)
        JOIN season_cost sc USING (season_key)
        ORDER BY f.farm_code, s.season_code, fi.field_code, t.activity_type
        """,
    )


def _cost_activity_detail(connection: sqlite3.Connection) -> pd.DataFrame:
    return _query(
        connection,
        """
        SELECT a.activity_id,
               a.occurred_at,
               substr(d.full_date, 1, 7) AS month,
               f.farm_code,
               f.farm_name,
               fi.field_code,
               fi.field_name,
               s.season_code,
               c.crop_code,
               c.crop_name,
               t.activity_type,
               a.material_name,
               a.quantity_kg,
               a.labor_hours,
               a.material_cost_vnd AS operating_material_cost_vnd,
               a.labor_cost_vnd AS operating_labor_cost_vnd,
               a.total_cost_vnd AS operating_total_cost_vnd,
               a.notes
        FROM fact_crop_activity a
        JOIN dim_date d USING (date_key)
        JOIN dim_farm f USING (farm_key)
        JOIN dim_field fi USING (field_key)
        JOIN dim_season s USING (season_key)
        JOIN dim_crop c USING (crop_key)
        JOIN dim_activity_type t USING (activity_type_key)
        ORDER BY f.farm_code,
                 s.season_code,
                 fi.field_code,
                 a.occurred_at,
                 a.activity_id
        """,
    )


def _cost_reconciliation(connection: sqlite3.Connection) -> pd.DataFrame:
    return _query(
        connection,
        """
        WITH season_cost AS (
            SELECT season_key,
                   SUM(material_cost_vnd) AS material_cost_vnd,
                   SUM(labor_cost_vnd) AS labor_cost_vnd,
                   SUM(total_cost_vnd) AS total_cost_vnd
            FROM fact_crop_activity
            GROUP BY season_key
        )
        SELECT f.farm_code,
               s.season_code,
               s.budget_cost_vnd AS budget_operating_cost_vnd,
               COALESCE(sc.material_cost_vnd, 0) AS operating_material_cost_vnd,
               COALESCE(sc.labor_cost_vnd, 0) AS operating_labor_cost_vnd,
               COALESCE(sc.total_cost_vnd, 0) AS operating_total_cost_vnd,
               COALESCE(sc.material_cost_vnd, 0) + COALESCE(sc.labor_cost_vnd, 0)
                   AS operating_component_total_vnd,
               COALESCE(sc.total_cost_vnd, 0)
                   - COALESCE(sc.material_cost_vnd, 0)
                   - COALESCE(sc.labor_cost_vnd, 0) AS component_delta_vnd,
               COALESCE(sc.total_cost_vnd, 0) - s.budget_cost_vnd AS budget_variance_vnd,
               CASE WHEN ABS(
                       COALESCE(sc.total_cost_vnd, 0)
                       - COALESCE(sc.material_cost_vnd, 0)
                       - COALESCE(sc.labor_cost_vnd, 0)
                    ) <= 0.01
                    THEN 'balanced'
                    ELSE 'mismatch' END AS reconciliation_status
        FROM dim_season s
        JOIN dim_farm f USING (farm_key)
        LEFT JOIN season_cost sc USING (season_key)
        ORDER BY f.farm_code, s.season_code
        """,
    )


def build_cost_analysis_gold(
    connection: sqlite3.Connection,
) -> dict[str, pd.DataFrame]:
    """Build auditable operating-cost and separate procurement Gold contracts."""

    cost_gold = {
        "cost_summary": _cost_summary(connection),
        "cost_monthly": _cost_monthly(connection),
        "cost_farm": _cost_farm(connection),
        "cost_season": _cost_season(connection),
        "cost_activity": _cost_activity(connection),
        "cost_activity_detail": _cost_activity_detail(connection),
        "cost_reconciliation": _cost_reconciliation(connection),
    }
    result = {**cost_gold, **build_procurement_gold(connection)}
    validate_cost_gold_contracts(result)
    return result
