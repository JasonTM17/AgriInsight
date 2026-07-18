from __future__ import annotations

from datetime import date
from typing import Any

import pandas as pd


def _billions(value: float) -> str:
    return f"{value / 1_000_000_000:,.2f} tỷ đồng"


def _number(value: Any) -> int | float | str | None:
    if pd.isna(value):
        return None
    if hasattr(value, "item"):
        return value.item()
    return value


def build_insights(gold: dict[str, pd.DataFrame], as_of_date: date) -> dict[str, Any]:
    insights: list[dict[str, Any]] = []

    farms = gold["farm_performance"]
    if not farms.empty:
        best_farm = farms.sort_values("profit_vnd", ascending=False).iloc[0]
        insights.append(
            {
                "code": "FARM_HIGHEST_PROFIT",
                "severity": "info",
                "title": "Trang trại có lợi nhuận cao nhất",
                "summary": (
                    f"{best_farm['farm_name']} dẫn đầu với lợi nhuận "
                    f"{_billions(float(best_farm['profit_vnd']))} và biên lợi nhuận "
                    f"{float(best_farm['profit_margin_pct']):.1f}%."
                ),
                "evidence": {
                    "farm_code": _number(best_farm["farm_code"]),
                    "profit_vnd": _number(best_farm["profit_vnd"]),
                    "profit_margin_pct": _number(best_farm["profit_margin_pct"]),
                },
            }
        )

    crops = gold["crop_profitability"]
    profitable_crops = crops[crops["profit_vnd"] > 0]
    if not profitable_crops.empty:
        best_crop = profitable_crops.sort_values("profit_vnd", ascending=False).iloc[0]
        insights.append(
            {
                "code": "CROP_HIGHEST_PROFIT",
                "severity": "info",
                "title": "Cây trồng đóng góp lợi nhuận lớn nhất",
                "summary": (
                    f"{best_crop['crop_name']} tạo ra {_billions(float(best_crop['profit_vnd']))} "
                    f"lợi nhuận trên dữ liệu hiện có."
                ),
                "evidence": {
                    "crop_code": _number(best_crop["crop_code"]),
                    "profit_vnd": _number(best_crop["profit_vnd"]),
                    "revenue_vnd": _number(best_crop["total_revenue_vnd"]),
                },
            }
        )

    costs = gold["cost_breakdown"]
    if not costs.empty:
        largest_cost = costs.sort_values("total_cost_vnd", ascending=False).iloc[0]
        insights.append(
            {
                "code": "LARGEST_COST_DRIVER",
                "severity": "watch",
                "title": "Nhóm chi phí cần theo dõi",
                "summary": (
                    f"{largest_cost['activity_type']} là nhóm chi phí lớn nhất, chiếm "
                    f"{float(largest_cost['share_pct']):.1f}% tổng chi phí chăm sóc."
                ),
                "evidence": {
                    "activity_type": _number(largest_cost["activity_type"]),
                    "total_cost_vnd": _number(largest_cost["total_cost_vnd"]),
                    "share_pct": _number(largest_cost["share_pct"]),
                },
            }
        )

    risk_alerts = gold["risk_alerts"]
    if not risk_alerts.empty:
        insights.append(
            {
                "code": "SEASON_RISK_ALERTS",
                "severity": "warning",
                "title": "Mùa vụ cần xử lý",
                "summary": (
                    f"Có {len(risk_alerts)} mùa vụ vượt ngân sách hoặc có sản lượng thấp hơn "
                    "85% mục tiêu."
                ),
                "evidence": {
                    "alert_count": len(risk_alerts),
                    "over_budget_count": int((risk_alerts["risk_type"] == "over_budget").sum()),
                    "yield_risk_count": int(
                        (risk_alerts["risk_type"] == "yield_below_target").sum()
                    ),
                },
            }
        )

    inventory_summary = gold.get("inventory_summary", pd.DataFrame())
    inventory_alerts = gold.get("inventory_alerts", pd.DataFrame())
    if not inventory_summary.empty:
        inventory = inventory_summary.iloc[0]
        critical = int(inventory["critical_alerts"])
        low_stock = int(inventory["low_stock_skus"])
        if critical or low_stock:
            top_alert = (
                inventory_alerts.sort_values(
                    "severity",
                    key=lambda values: values.map(
                        {"critical": 0, "warning": 1, "watch": 2}
                    ).fillna(3),
                ).iloc[0]
                if not inventory_alerts.empty
                else None
            )
            detail = (
                f" Ưu tiên {top_alert['material_name']} tại {top_alert['warehouse_name']}."
                if top_alert is not None
                else ""
            )
            insights.append(
                {
                    "code": "INVENTORY_REPLENISHMENT_RISK",
                    "severity": "warning" if critical else "watch",
                    "title": "Rủi ro tồn kho cần xử lý",
                    "summary": (
                        f"Có {low_stock} SKU-location dưới điểm đặt hàng, trong đó "
                        f"{critical} cảnh báo nghiêm trọng.{detail}"
                    ),
                    "evidence": {
                        "low_stock_skus": low_stock,
                        "critical_alerts": critical,
                        "expiring_30d_skus": int(inventory["expiring_30d_skus"]),
                    },
                }
            )

    health_summary = gold.get("crop_health_summary", pd.DataFrame())
    if not health_summary.empty:
        health = health_summary.iloc[0]
        high_risk = int(health["high_risk_fields"])
        offline = int(health["offline_sensors"])
        if high_risk or offline:
            insights.append(
                {
                    "code": "CROP_HEALTH_RISK",
                    "severity": "warning",
                    "title": "Vùng trồng có rủi ro sức khỏe cây trồng",
                    "summary": (
                        f"Có {high_risk} khu vực rủi ro cao và {offline} cảm biến không gửi "
                        "dữ liệu đúng hạn; cần ưu tiên kiểm tra các khu vực đứng đầu risk score."
                    ),
                    "evidence": {
                        "high_risk_fields": high_risk,
                        "offline_sensors": offline,
                        "pest_cases_90d": int(health["pest_cases_90d"]),
                    },
                }
            )

    return {
        "as_of_date": as_of_date.isoformat(),
        "insight_count": len(insights),
        "insights": insights,
    }
