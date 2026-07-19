from __future__ import annotations

from collections.abc import Mapping

import pandas as pd
import streamlit as st


def _labels(frame: pd.DataFrame, code: str, name: str) -> dict[str, str]:
    if frame.empty:
        return {}
    return {
        str(row[code]): f"{row[name]} ({row[code]})"
        for _, row in frame.loc[:, [code, name]].drop_duplicates().iterrows()
    }


def _option_label(value: str | None, labels: Mapping[str, str]) -> str:
    return "Tất cả" if value is None else labels.get(value, value)


def _request_mapping(scope: str, **values: object) -> dict[str, object]:
    return {
        "scope": scope,
        **{key: value for key, value in values.items() if value is not None},
    }


def render_operating_form(
    gold: Mapping[str, pd.DataFrame],
) -> tuple[bool, dict[str, object]]:
    detail = gold["cost_activity_detail"]
    farm_labels = _labels(detail, "farm_code", "farm_name")
    crop_labels = _labels(detail, "crop_code", "crop_name")
    farms = (None, *sorted(farm_labels))
    crops = (None, *sorted(crop_labels))
    seasons = (None, *sorted(str(value) for value in detail["season_code"].unique()))
    activities = (
        None,
        *sorted(str(value) for value in detail["activity_type"].unique()),
    )
    months = (None, *sorted(str(value) for value in detail["month"].unique()))

    with st.form("cost_operating_form"):
        first = st.columns(3)
        farm = first[0].selectbox(
            "Nông trại",
            farms,
            format_func=lambda value: _option_label(value, farm_labels),
            key="cost_operating_farm",
        )
        crop = first[1].selectbox(
            "Cây trồng",
            crops,
            format_func=lambda value: _option_label(value, crop_labels),
            key="cost_operating_crop",
        )
        season = first[2].selectbox(
            "Mùa vụ",
            seasons,
            format_func=lambda value: _option_label(value, {}),
            key="cost_operating_season",
        )
        second = st.columns(3)
        activity = second[0].selectbox(
            "Hoạt động",
            activities,
            format_func=lambda value: _option_label(value, {}),
            key="cost_operating_activity",
        )
        month = second[1].selectbox(
            "Tháng",
            months,
            format_func=lambda value: _option_label(value, {}),
            key="cost_operating_month",
        )
        top_n = second[2].number_input(
            "Top N",
            min_value=1,
            max_value=30,
            value=15,
            step=1,
            key="cost_operating_top_n",
        )
        submitted = st.form_submit_button(
            "Áp dụng và tạo report",
            key="cost_operating_submit",
            width="stretch",
        )
    return submitted, _request_mapping(
        "operating",
        farm=farm,
        crop=crop,
        season=season,
        activity=activity,
        month_from=month,
        month_to=month,
        top_n=int(top_n),
    )


def render_procurement_form(
    gold: Mapping[str, pd.DataFrame],
) -> tuple[bool, dict[str, object]]:
    detail = gold["procurement_detail"]
    farm_labels = _labels(detail, "farm_code", "farm_name")
    supplier_labels = _labels(detail, "supplier_code", "supplier_name")
    farms = (None, *sorted(farm_labels))
    suppliers = (None, *sorted(supplier_labels))
    months = (None, *sorted(str(value) for value in detail["month"].unique()))

    with st.form("cost_procurement_form"):
        columns = st.columns(4)
        farm = columns[0].selectbox(
            "Nông trại",
            farms,
            format_func=lambda value: _option_label(value, farm_labels),
            key="cost_procurement_farm",
        )
        supplier = columns[1].selectbox(
            "Nhà cung cấp",
            suppliers,
            format_func=lambda value: _option_label(value, supplier_labels),
            key="cost_procurement_supplier",
        )
        month = columns[2].selectbox(
            "Tháng",
            months,
            format_func=lambda value: _option_label(value, {}),
            key="cost_procurement_month",
        )
        top_n = columns[3].number_input(
            "Top N",
            min_value=1,
            max_value=30,
            value=15,
            step=1,
            key="cost_procurement_top_n",
        )
        submitted = st.form_submit_button(
            "Áp dụng và tạo report",
            key="cost_procurement_submit",
            width="stretch",
        )
    return submitted, _request_mapping(
        "procurement",
        farm=farm,
        supplier=supplier,
        month_from=month,
        month_to=month,
        top_n=int(top_n),
    )


__all__ = ["render_operating_form", "render_procurement_form"]
