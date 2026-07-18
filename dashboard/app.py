from __future__ import annotations

import json
import os
from pathlib import Path

import pandas as pd
import plotly.express as px
import streamlit as st


st.set_page_config(
    page_title="AgriInsight Analytics",
    page_icon="🌾",
    layout="wide",
)

RISK_COLORS = {"healthy": "#2E7D32", "watch": "#F9A825", "high": "#C62828"}
STOCK_COLORS = {
    "healthy": "#2E7D32",
    "low_stock": "#F9A825",
    "stockout": "#C62828",
    "overstock": "#1565C0",
}


def _artifact_root() -> Path:
    configured = os.getenv("AGRIINSIGHT_ARTIFACTS")
    if configured:
        return Path(configured).resolve()
    return (Path(__file__).resolve().parents[1] / "artifacts").resolve()


@st.cache_data
def _load_csv(path: Path) -> pd.DataFrame:
    return pd.read_csv(path)


@st.cache_data
def _load_json(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def _money(value: float) -> str:
    return f"{value / 1_000_000_000:,.2f} tỷ ₫"


def _number(value: float) -> str:
    return f"{value:,.0f}"


def _executive_page(data: dict[str, object]) -> None:
    executive = data["executive"].iloc[0]
    monthly = data["monthly"]
    costs = data["costs"]
    insights = data["insights"]

    st.header("Tổng quan điều hành")
    metric_columns = st.columns(4)
    metric_columns[0].metric("Doanh thu", _money(float(executive["total_revenue_vnd"])))
    metric_columns[1].metric("Chi phí", _money(float(executive["total_cost_vnd"])))
    metric_columns[2].metric(
        "Lợi nhuận",
        _money(float(executive["profit_vnd"])),
        f"{float(executive['profit_margin_pct']):.1f}% biên lợi nhuận",
    )
    metric_columns[3].metric(
        "Sản lượng", f"{_number(float(executive['harvest_quantity_kg']) / 1_000)} tấn"
    )

    secondary_columns = st.columns(4)
    secondary_columns[0].metric(
        "Diện tích canh tác", f"{float(executive['cultivated_area_ha']):,.1f} ha"
    )
    secondary_columns[1].metric("Mùa vụ đang hoạt động", int(executive["active_seasons"]))
    secondary_columns[2].metric("Tổng cảnh báo rủi ro", int(executive["risk_alerts"]))
    secondary_columns[3].metric(
        "Data validity", f"{float(data['quality']['scores']['after']['validity_pct']):.1f}%"
    )

    left, right = st.columns((1.6, 1))
    with left:
        st.subheader("Doanh thu và chi phí theo tháng")
        monthly_long = monthly.melt(
            id_vars="month",
            value_vars=("revenue_vnd", "cost_vnd"),
            var_name="metric",
            value_name="value_vnd",
        )
        monthly_long["metric"] = monthly_long["metric"].map(
            {"revenue_vnd": "Doanh thu", "cost_vnd": "Chi phí"}
        )
        chart = px.line(
            monthly_long,
            x="month",
            y="value_vnd",
            color="metric",
            markers=True,
            labels={"month": "Tháng", "value_vnd": "VND", "metric": "Chỉ số"},
            color_discrete_map={"Doanh thu": "#2E7D32", "Chi phí": "#EF6C00"},
        )
        chart.update_layout(legend_title_text="", hovermode="x unified")
        st.plotly_chart(chart, width="stretch", key="executive_monthly")
    with right:
        st.subheader("Cơ cấu chi phí chăm sóc")
        chart = px.pie(
            costs,
            values="total_cost_vnd",
            names="activity_type",
            hole=0.55,
            color_discrete_sequence=px.colors.qualitative.Safe,
        )
        chart.update_traces(textposition="inside", textinfo="percent")
        st.plotly_chart(chart, width="stretch", key="executive_cost_mix")

    risk_breakdown = pd.DataFrame(
        {
            "Nhóm": ["Mùa vụ", "Kho vật tư", "Sức khỏe cây trồng"],
            "Cảnh báo": [
                int(executive["season_risk_alerts"]),
                int(executive["inventory_risk_alerts"]),
                int(executive["crop_health_risk_alerts"]),
            ],
        }
    )
    st.subheader("Cảnh báo theo domain")
    chart = px.bar(
        risk_breakdown,
        x="Nhóm",
        y="Cảnh báo",
        color="Nhóm",
        color_discrete_sequence=("#EF6C00", "#1565C0", "#C62828"),
    )
    chart.update_layout(showlegend=False)
    st.plotly_chart(chart, width="stretch", key="executive_risk_breakdown")

    st.subheader("Insight và khuyến nghị")
    columns = st.columns(2)
    for index, insight in enumerate(insights["insights"]):
        with columns[index % 2]:
            content = f"**{insight['title']}**\n\n{insight['summary']}"
            if insight["severity"] == "warning":
                st.warning(content)
            elif insight["severity"] == "watch":
                st.info(content, icon="👀")
            else:
                st.info(content)


def _farm_page(data: dict[str, object]) -> None:
    farms = data["farms"]
    crops = data["crops"]
    st.header("Hiệu suất trang trại")
    selected = st.selectbox(
        "Drill-down theo trang trại",
        ["Tất cả", *farms["farm_name"].sort_values().tolist()],
        key="farm_filter",
    )
    filtered = farms if selected == "Tất cả" else farms[farms["farm_name"] == selected]

    columns = st.columns(4)
    columns[0].metric("Doanh thu", _money(float(filtered["total_revenue_vnd"].sum())))
    columns[1].metric("Chi phí", _money(float(filtered["total_cost_vnd"].sum())))
    columns[2].metric("Lợi nhuận", _money(float(filtered["profit_vnd"].sum())))
    columns[3].metric(
        "Năng suất TB",
        f"{float(filtered['yield_kg_per_ha'].mean()):,.0f} kg/ha",
    )

    left, right = st.columns((1.25, 1))
    with left:
        chart = px.bar(
            filtered.sort_values("profit_vnd"),
            x="profit_vnd",
            y="farm_name",
            orientation="h",
            color="profit_margin_pct",
            labels={
                "profit_vnd": "Lợi nhuận (VND)",
                "farm_name": "Trang trại",
                "profit_margin_pct": "Biên LN (%)",
            },
            color_continuous_scale="RdYlGn",
            title="Lợi nhuận theo trang trại",
        )
        st.plotly_chart(chart, width="stretch", key="farm_profit")
    with right:
        chart = px.scatter(
            filtered,
            x="cost_vnd_per_ha",
            y="yield_kg_per_ha",
            size="cultivated_area_ha",
            color="profit_margin_pct",
            hover_name="farm_name",
            labels={
                "cost_vnd_per_ha": "Chi phí/ha",
                "yield_kg_per_ha": "Năng suất kg/ha",
                "profit_margin_pct": "Biên LN (%)",
            },
            color_continuous_scale="RdYlGn",
            title="Chi phí và năng suất",
        )
        st.plotly_chart(chart, width="stretch", key="farm_efficiency")

    st.subheader("Lợi nhuận theo cây trồng")
    crop_view = crops[
        [
            "crop_name",
            "operated_area_ha",
            "harvest_quantity_kg",
            "total_revenue_vnd",
            "total_cost_vnd",
            "profit_vnd",
            "profit_margin_pct",
        ]
    ].copy()
    crop_view.columns = [
        "Cây trồng",
        "Diện tích-vụ (ha)",
        "Sản lượng (kg)",
        "Doanh thu",
        "Chi phí",
        "Lợi nhuận",
        "Biên LN (%)",
    ]
    st.dataframe(crop_view, hide_index=True, width="stretch")


def _inventory_page(data: dict[str, object]) -> None:
    summary = data["inventory_summary"].iloc[0]
    status = data["inventory_status"]
    abc = data["inventory_abc"]
    movements = data["inventory_movements"]
    alerts = data["inventory_alerts"]

    st.header("Quản lý và phân tích kho vật tư")
    primary_metrics = st.columns(3)
    primary_metrics[0].metric(
        "Giá trị tồn kho", _money(float(summary["total_inventory_value_vnd"]))
    )
    primary_metrics[1].metric("SKU dưới ngưỡng", int(summary["low_stock_skus"]))
    primary_metrics[2].metric("SKU hết hàng", int(summary["stockout_skus"]))
    secondary_metrics = st.columns(3)
    secondary_metrics[0].metric("Sắp hết hạn", int(summary["expiring_30d_skus"]))
    secondary_metrics[1].metric("Tồn quá mức", int(summary["overstock_skus"]))
    secondary_metrics[2].metric(
        "Days of supply TB", f"{float(summary['average_days_of_supply']):.1f} ngày"
    )

    filter_columns = st.columns(3)
    warehouse = filter_columns[0].selectbox(
        "Kho",
        ["Tất cả", *status["warehouse_name"].drop_duplicates().sort_values().tolist()],
        key="inventory_warehouse_filter",
    )
    category = filter_columns[1].selectbox(
        "Nhóm vật tư",
        ["Tất cả", *status["category"].drop_duplicates().sort_values().tolist()],
        key="inventory_category_filter",
    )
    stock_state = filter_columns[2].selectbox(
        "Trạng thái tồn kho",
        ["Tất cả", "healthy", "low_stock", "stockout", "overstock"],
        key="inventory_status_filter",
    )
    filtered = status.copy()
    if warehouse != "Tất cả":
        filtered = filtered[filtered["warehouse_name"] == warehouse]
    if category != "Tất cả":
        filtered = filtered[filtered["category"] == category]
    if stock_state != "Tất cả":
        filtered = filtered[filtered["stock_status"] == stock_state]

    left, right = st.columns((1.2, 1))
    with left:
        chart_data = filtered.sort_values("inventory_value_vnd", ascending=False).head(30)
        chart = px.bar(
            chart_data,
            x="inventory_value_vnd",
            y="material_name",
            color="stock_status",
            orientation="h",
            hover_data=("warehouse_name", "stock_quantity", "base_unit", "days_of_supply"),
            labels={
                "inventory_value_vnd": "Giá trị tồn kho (VND)",
                "material_name": "Vật tư",
                "stock_status": "Trạng thái",
            },
            color_discrete_map=STOCK_COLORS,
            title="Giá trị tồn kho và trạng thái",
        )
        chart.update_layout(yaxis={"categoryorder": "total ascending"})
        st.plotly_chart(chart, width="stretch", key="inventory_value_status")
    with right:
        chart = px.treemap(
            abc,
            path=("abc_class", "material_name"),
            values="inventory_value_vnd",
            color="abc_class",
            color_discrete_map={"A": "#C62828", "B": "#F9A825", "C": "#2E7D32"},
            title="ABC Analysis theo giá trị tồn kho",
        )
        st.plotly_chart(chart, width="stretch", key="inventory_abc")

    movement_chart = px.bar(
        movements,
        x="month",
        y="movement_value_vnd",
        color="transaction_type",
        barmode="group",
        labels={
            "month": "Tháng",
            "movement_value_vnd": "Giá trị giao dịch (VND)",
            "transaction_type": "Loại giao dịch",
        },
        color_discrete_map={"IN": "#1565C0", "OUT": "#EF6C00"},
        title="Nhập và xuất kho theo tháng",
    )
    st.plotly_chart(movement_chart, width="stretch", key="inventory_movements")

    st.subheader(f"Chi tiết tồn kho ({len(filtered)} SKU-location)")
    detail_columns = (
        "farm_name",
        "warehouse_name",
        "material_name",
        "category",
        "abc_class",
        "stock_quantity",
        "base_unit",
        "reorder_point",
        "days_of_supply",
        "nearest_expiry_date",
        "stock_status",
        "recommended_order_quantity",
        "predicted_30d_need",
    )
    st.dataframe(filtered[list(detail_columns)], hide_index=True, width="stretch")
    if not alerts.empty:
        with st.expander(f"Chi tiết {len(alerts)} cảnh báo kho"):
            st.dataframe(alerts, hide_index=True, width="stretch")


def _crop_health_page(data: dict[str, object]) -> None:
    summary = data["health_summary"].iloc[0]
    field_status = data["field_health"]
    environment = data["environment"]
    pest_weekly = data["pest_weekly"]
    alerts = data["health_alerts"]

    st.header("Sức khỏe cây trồng và môi trường")
    primary_metrics = st.columns(3)
    primary_metrics[0].metric("Khu vực theo dõi", int(summary["monitored_fields"]))
    primary_metrics[1].metric("Khu vực rủi ro cao", int(summary["high_risk_fields"]))
    primary_metrics[2].metric("Cần theo dõi", int(summary["watch_fields"]))
    secondary_metrics = st.columns(3)
    secondary_metrics[0].metric("Cảm biến offline", int(summary["offline_sensors"]))
    secondary_metrics[1].metric("Ca sâu bệnh 90 ngày", int(summary["pest_cases_90d"]))
    secondary_metrics[2].metric(
        "Độ ẩm đất TB", f"{float(summary['average_soil_moisture_pct']):.1f}%"
    )

    filter_columns = st.columns(2)
    farm = filter_columns[0].selectbox(
        "Trang trại",
        ["Tất cả", *field_status["farm_name"].drop_duplicates().sort_values().tolist()],
        key="health_farm_filter",
    )
    risk = filter_columns[1].selectbox(
        "Mức rủi ro",
        ["Tất cả", "healthy", "watch", "high"],
        key="health_risk_filter",
    )
    filtered = field_status.copy()
    if farm != "Tất cả":
        filtered = filtered[filtered["farm_name"] == farm]
    if risk != "Tất cả":
        filtered = filtered[filtered["risk_status"] == risk]

    left, right = st.columns((1.1, 1))
    with left:
        chart = px.bar(
            filtered.sort_values("risk_score"),
            x="risk_score",
            y="field_name",
            color="risk_status",
            orientation="h",
            hover_data=("farm_name", "crop_name", "soil_moisture_pct", "pest_cases_90d"),
            labels={
                "risk_score": "Risk score",
                "field_name": "Khu vực",
                "risk_status": "Mức rủi ro",
            },
            color_discrete_map=RISK_COLORS,
            title="Risk score theo khu vực",
        )
        st.plotly_chart(chart, width="stretch", key="health_risk_score")
    with right:
        chart = px.scatter(
            filtered,
            x="soil_moisture_pct",
            y="temperature_c",
            size="area_ha",
            color="risk_status",
            hover_name="field_name",
            hover_data=("farm_name", "crop_name", "soil_ph", "rainfall_7d_mm"),
            labels={
                "soil_moisture_pct": "Độ ẩm đất (%)",
                "temperature_c": "Nhiệt độ (°C)",
                "risk_status": "Mức rủi ro",
            },
            color_discrete_map=RISK_COLORS,
            title="Quan hệ độ ẩm – nhiệt độ – rủi ro",
        )
        st.plotly_chart(chart, width="stretch", key="health_environment_scatter")

    field_options = filtered["field_name"].tolist()
    if field_options:
        selected_field = st.selectbox(
            "Drill-down lịch sử cảm biến theo khu vực",
            field_options,
            key="health_field_filter",
        )
        selected_code = filtered.loc[
            filtered["field_name"] == selected_field, "field_code"
        ].iloc[0]
        daily = environment[environment["field_code"] == selected_code]
        daily_long = daily.melt(
            id_vars="reading_date",
            value_vars=("soil_moisture_pct", "air_humidity_pct"),
            var_name="metric",
            value_name="value_pct",
        )
        daily_long["metric"] = daily_long["metric"].map(
            {"soil_moisture_pct": "Độ ẩm đất", "air_humidity_pct": "Độ ẩm không khí"}
        )
        chart = px.line(
            daily_long,
            x="reading_date",
            y="value_pct",
            color="metric",
            labels={"reading_date": "Ngày", "value_pct": "%", "metric": "Chỉ số"},
            title=f"Diễn biến độ ẩm — {selected_field}",
        )
        st.plotly_chart(chart, width="stretch", key="health_daily_history")

    if not pest_weekly.empty:
        chart = px.bar(
            pest_weekly,
            x="week",
            y="case_count",
            color="pest_name",
            labels={"week": "Tuần", "case_count": "Số ca", "pest_name": "Loại sâu bệnh"},
            title="Ca sâu bệnh theo tuần",
        )
        st.plotly_chart(chart, width="stretch", key="health_pest_weekly")

    st.subheader(f"Chi tiết khu vực ({len(filtered)})")
    st.dataframe(filtered, hide_index=True, width="stretch")
    if not alerts.empty:
        with st.expander(f"Chi tiết {len(alerts)} cảnh báo sức khỏe cây trồng"):
            st.dataframe(alerts, hide_index=True, width="stretch")


def _data_quality_page(data: dict[str, object]) -> None:
    quality = data["quality"]
    st.header("Chất lượng và độ tin cậy dữ liệu")
    quality_view = pd.DataFrame(
        {
            "Chỉ số": ["Completeness", "Validity", "Uniqueness", "Freshness"],
            "Bronze (%)": [
                quality["scores"]["before"]["completeness_pct"],
                quality["scores"]["before"]["validity_pct"],
                quality["scores"]["before"]["uniqueness_pct"],
                quality["scores"]["before"]["freshness_pct"],
            ],
            "Silver (%)": [
                quality["scores"]["after"]["completeness_pct"],
                quality["scores"]["after"]["validity_pct"],
                quality["scores"]["after"]["uniqueness_pct"],
                quality["scores"]["after"]["freshness_pct"],
            ],
        }
    )
    chart = px.bar(
        quality_view.melt(id_vars="Chỉ số", var_name="Lớp", value_name="Điểm (%)"),
        x="Chỉ số",
        y="Điểm (%)",
        color="Lớp",
        barmode="group",
        color_discrete_map={"Bronze (%)": "#EF6C00", "Silver (%)": "#2E7D32"},
        range_y=(0, 105),
        title="Data quality trước và sau xử lý",
    )
    st.plotly_chart(chart, width="stretch", key="data_quality_scores")
    st.dataframe(quality_view, hide_index=True, width="stretch")

    before_failures = pd.DataFrame(quality["checks"]["before"])
    before_failures = before_failures[before_failures["failed_rows"] > 0]
    st.subheader("Lỗi phát hiện tại Bronze")
    st.dataframe(before_failures, hide_index=True, width="stretch")
    st.subheader("Hành động remediation")
    remediation = pd.DataFrame(
        [
            {"Hành động": action, "Số lượng": count}
            for action, count in quality["remediation_actions"].items()
        ]
    )
    st.dataframe(remediation, hide_index=True, width="stretch")


root = _artifact_root()
required = (
    root / "gold" / "executive_summary.csv",
    root / "gold" / "inventory_summary.csv",
    root / "gold" / "crop_health_summary.csv",
    root / "quality" / "data_quality_report.json",
)
missing = [path for path in required if not path.exists()]
if missing:
    st.error("Chưa có artifact để hiển thị.")
    st.code("python -m agriinsight run --output artifacts")
    st.caption("Thiếu: " + ", ".join(str(path) for path in missing))
    st.stop()

data: dict[str, object] = {
    "executive": _load_csv(root / "gold" / "executive_summary.csv"),
    "monthly": _load_csv(root / "gold" / "monthly_financials.csv"),
    "farms": _load_csv(root / "gold" / "farm_performance.csv"),
    "crops": _load_csv(root / "gold" / "crop_profitability.csv"),
    "costs": _load_csv(root / "gold" / "cost_breakdown.csv"),
    "risks": _load_csv(root / "gold" / "risk_alerts.csv"),
    "inventory_summary": _load_csv(root / "gold" / "inventory_summary.csv"),
    "inventory_status": _load_csv(root / "gold" / "inventory_status.csv"),
    "inventory_abc": _load_csv(root / "gold" / "inventory_abc.csv"),
    "inventory_movements": _load_csv(root / "gold" / "inventory_movements_monthly.csv"),
    "inventory_alerts": _load_csv(root / "gold" / "inventory_alerts.csv"),
    "health_summary": _load_csv(root / "gold" / "crop_health_summary.csv"),
    "field_health": _load_csv(root / "gold" / "field_health_status.csv"),
    "environment": _load_csv(root / "gold" / "environment_daily.csv"),
    "pest_weekly": _load_csv(root / "gold" / "pest_incidents_weekly.csv"),
    "health_alerts": _load_csv(root / "gold" / "crop_health_alerts.csv"),
    "quality": _load_json(root / "quality" / "data_quality_report.json"),
    "insights": _load_json(root / "gold" / "insights.json"),
}

st.title("AgriInsight — Enterprise Agriculture Analytics")
st.caption(
    f"Ngày chốt dữ liệu: {data['quality']['as_of_date']} · "
    f"Data quality: {data['quality']['status'].upper()}"
)
page = st.sidebar.radio(
    "Dashboard",
    ("Executive", "Farm Performance", "Inventory", "Crop Health", "Data Quality"),
)
st.sidebar.caption("Bronze → Silver → Star Schema → Gold")

if page == "Executive":
    _executive_page(data)
elif page == "Farm Performance":
    _farm_page(data)
elif page == "Inventory":
    _inventory_page(data)
elif page == "Crop Health":
    _crop_health_page(data)
else:
    _data_quality_page(data)
