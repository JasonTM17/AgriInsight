from __future__ import annotations

import plotly.express as px
import streamlit as st

from agriinsight.cost_dashboard import OperatingDashboardView
from agriinsight.cost_report_contract import CostReportBundle
from dashboard.cost_analysis_formatting import format_vnd


_XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"


def render_operating_view(view: OperatingDashboardView) -> None:
    columns = st.columns(4)
    columns[0].metric("Chi phí vận hành", format_vnd(view.operating_total_cost_vnd))
    columns[1].metric("Chênh lệch ngân sách", format_vnd(view.budget_variance_vnd))
    columns[2].metric("Chi phí / ha", format_vnd(view.operating_cost_per_ha_vnd))
    columns[3].metric("Chi phí / kg", format_vnd(view.operating_cost_per_kg_vnd))
    if view.context_is_broader:
        st.caption(
            "Ngân sách, chi phí/ha và chi phí/kg dùng ngữ cảnh mùa vụ vì Gold "
            "chưa phân bổ các KPI này theo hoạt động hoặc tháng."
        )

    st.subheader("Động lực chi phí theo hoạt động")
    if view.activity_drivers.empty:
        st.info("Không có hoạt động vận hành phù hợp với bộ lọc đã áp dụng.")
    else:
        chart = px.bar(
            view.activity_drivers,
            x="activity_type",
            y="operating_total_cost_vnd",
            color="activity_type",
            labels={
                "activity_type": "Hoạt động",
                "operating_total_cost_vnd": "Chi phí vận hành (VND)",
            },
        )
        chart.update_layout(showlegend=False, margin=dict(l=10, r=10, t=20, b=10))
        st.plotly_chart(chart, width="stretch", key="cost_operating_drivers_chart")
        drivers = view.activity_drivers.rename(
            columns={
                "activity_type": "Hoạt động",
                "activity_count": "Số lượt",
                "operating_material_cost_vnd": "Vật tư (VND)",
                "operating_labor_cost_vnd": "Nhân công (VND)",
                "operating_total_cost_vnd": "Tổng chi phí (VND)",
                "operating_cost_share_pct": "Tỷ trọng (%)",
            }
        )
        st.dataframe(
            drivers,
            hide_index=True,
            width="stretch",
            key="cost_operating_drivers_table",
        )

    with st.expander("Chi tiết hoạt động đã lọc"):
        if view.detail.empty:
            st.info("Không có dòng chi tiết để hiển thị.")
        else:
            columns = (
                "occurred_at",
                "farm_name",
                "field_name",
                "season_code",
                "crop_name",
                "activity_type",
                "material_name",
                "operating_total_cost_vnd",
            )
            st.dataframe(
                view.detail.loc[:, columns].head(200),
                hide_index=True,
                width="stretch",
                key="cost_operating_detail_table",
            )
            if len(view.detail) > 200:
                st.caption(f"Hiển thị 200/{len(view.detail):,} dòng; tải report để xem đủ.")


def render_download_controls(bundle: CostReportBundle, *, key_prefix: str) -> None:
    st.caption(
        f"Yêu cầu đã chuẩn hóa · scope={bundle.request.scope} · "
        f"filter={bundle.metadata.filter_hash}"
    )
    columns = st.columns(3)
    columns[0].download_button(
        "Tải CSV",
        data=bundle.csv.content,
        file_name=bundle.csv.filename,
        mime=bundle.csv.mime_type,
        key=f"{key_prefix}_csv",
        width="stretch",
    )
    columns[1].download_button(
        "Tải PDF",
        data=bundle.pdf.content,
        file_name=bundle.pdf.filename,
        mime=bundle.pdf.mime_type,
        key=f"{key_prefix}_pdf",
        width="stretch",
    )
    if bundle.xlsx is not None:
        columns[2].download_button(
            "Tải XLSX",
            data=bundle.xlsx.content,
            file_name=bundle.xlsx.filename,
            mime=bundle.xlsx.mime_type,
            key=f"{key_prefix}_xlsx",
            width="stretch",
        )
    else:
        columns[2].download_button(
            "XLSX không khả dụng",
            data=b"",
            file_name="cost-analysis-unavailable.xlsx",
            mime=_XLSX_MIME,
            key=f"{key_prefix}_xlsx_unavailable",
            disabled=True,
            width="stretch",
        )
        st.caption("XLSX adapter chưa được cấp phát trên máy chủ; CSV/PDF vẫn dùng được.")


__all__ = [
    "render_download_controls",
    "render_operating_view",
]
