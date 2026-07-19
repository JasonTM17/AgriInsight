from __future__ import annotations

import plotly.express as px
import streamlit as st

from agriinsight.cost_dashboard import ProcurementDashboardView
from dashboard.cost_analysis_formatting import format_number, format_vnd


def render_procurement_view(view: ProcurementDashboardView) -> None:
    columns = st.columns(3)
    columns[0].metric("Chi tiêu mua hàng", format_vnd(view.procurement_spend_vnd))
    columns[1].metric("Số giao dịch", f"{view.transaction_count:,}")
    columns[2].metric(
        "Số lượng quy đổi đơn vị gốc",
        format_number(view.procurement_quantity_base_unit),
    )

    st.subheader("Nhà cung cấp → kho → vật tư")
    if view.material_drivers.empty:
        st.info("Không có giao dịch mua hàng phù hợp với bộ lọc đã áp dụng.")
    else:
        chart_data = view.material_drivers.assign(
            supplier_label=lambda frame: frame["supplier_code"].astype(str),
            warehouse_label=lambda frame: frame["warehouse_code"].astype(str),
            material_label=lambda frame: frame["material_code"].astype(str),
        )
        chart = px.bar(
            chart_data,
            x="warehouse_label",
            y="procurement_spend_vnd",
            color="supplier_label",
            pattern_shape="material_label",
            hover_data={
                "supplier_name": True,
                "warehouse_name": True,
                "material_name": True,
            },
            labels={
                "warehouse_label": "Kho",
                "procurement_spend_vnd": "Chi tiêu mua hàng (VND)",
                "supplier_label": "Nhà cung cấp",
                "material_label": "Vật tư",
            },
        )
        chart.update_layout(
            barmode="stack",
            height=600,
            legend=dict(
                orientation="h",
                yanchor="top",
                y=-0.2,
                xanchor="left",
                x=0,
                title_text="",
            ),
            margin=dict(l=10, r=10, t=20, b=180),
        )
        st.plotly_chart(chart, width="stretch", key="cost_procurement_drivers_chart")
        drivers = view.material_drivers.rename(
            columns={
                "farm_code": "Mã nông trại",
                "farm_name": "Nông trại",
                "supplier_code": "Mã nhà cung cấp",
                "supplier_name": "Nhà cung cấp",
                "warehouse_code": "Mã kho",
                "warehouse_name": "Kho",
                "material_code": "Mã vật tư",
                "material_name": "Vật tư",
                "base_unit": "Đơn vị",
                "transaction_count": "Số giao dịch",
                "procurement_quantity_base_unit": "Số lượng",
                "procurement_spend_vnd": "Chi tiêu (VND)",
            }
        )
        st.dataframe(
            drivers,
            hide_index=True,
            width="stretch",
            key="cost_procurement_drivers_table",
        )

    with st.expander("Chi tiết giao dịch mua hàng đã lọc"):
        if view.detail.empty:
            st.info("Không có dòng giao dịch để hiển thị.")
        else:
            columns = (
                "transaction_date",
                "farm_code",
                "farm_name",
                "warehouse_code",
                "warehouse_name",
                "supplier_code",
                "supplier_name",
                "material_code",
                "material_name",
                "base_unit",
                "procurement_quantity_base_unit",
                "procurement_spend_vnd",
                "batch_code",
                "expiry_date",
            )
            st.dataframe(
                view.detail.loc[:, columns].head(200),
                hide_index=True,
                width="stretch",
                key="cost_procurement_detail_table",
            )
            if len(view.detail) > 200:
                st.caption(f"Hiển thị 200/{len(view.detail):,} dòng; tải report để xem đủ.")


__all__ = ["render_procurement_view"]
