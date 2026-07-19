from __future__ import annotations

from collections.abc import Mapping
from pathlib import Path

import pandas as pd
import streamlit as st

from agriinsight.cost_dashboard import (
    prepare_operating_dashboard,
    prepare_procurement_dashboard,
)
from agriinsight.cost_report_contract import (
    CostReportDomains,
    ReportValidationError,
)
from agriinsight.metrics_cost_contracts import validate_cost_gold_contracts
from dashboard.cost_analysis_forms import (
    render_operating_form,
    render_procurement_form,
)
from dashboard.cost_analysis_presenters import (
    render_download_controls,
    render_operating_view,
)
from dashboard.cost_procurement_presenter import render_procurement_view
from dashboard.cost_analysis_session import (
    current_bundle,
    submit_report,
    validated_session_request,
)


_OPERATING_REQUEST_KEY = "cost_operating_request"
_OPERATING_BUNDLE_KEY = "cost_operating_bundle"
_OPERATING_SOURCE_KEY = "cost_operating_source_fingerprint"
_PROCUREMENT_REQUEST_KEY = "cost_procurement_request"
_PROCUREMENT_BUNDLE_KEY = "cost_procurement_bundle"
_PROCUREMENT_SOURCE_KEY = "cost_procurement_source_fingerprint"


def render_cost_analysis_page(
    gold: Mapping[str, pd.DataFrame],
    manifest: Mapping[str, object],
    *,
    temp_root: Path,
    source_fingerprint: str,
) -> None:
    st.header("Phân tích chi phí")
    st.warning("Dashboard nội bộ — chưa có xác thực và phân quyền theo dòng dữ liệu.")
    try:
        validate_cost_gold_contracts(gold)
        domains = CostReportDomains.from_gold(gold)
    except (TypeError, ValueError, ReportValidationError) as error:
        st.error(f"Cost Gold không hợp lệ: {error}")
        return

    operating_tab, procurement_tab = st.tabs(
        ("Chi phí vận hành", "Mua hàng — không cộng vào P&L")
    )
    with operating_tab:
        submitted, raw_request = render_operating_form(gold)
        if submitted:
            submit_report(
                raw_request=raw_request,
                gold=gold,
                manifest=manifest,
                domains=domains,
                temp_root=temp_root,
                request_key=_OPERATING_REQUEST_KEY,
                bundle_key=_OPERATING_BUNDLE_KEY,
                source_key=_OPERATING_SOURCE_KEY,
                source_fingerprint=source_fingerprint,
            )
        request = validated_session_request(
            _OPERATING_REQUEST_KEY,
            _OPERATING_BUNDLE_KEY,
            _OPERATING_SOURCE_KEY,
            "operating",
            domains,
        )
        render_operating_view(prepare_operating_dashboard(gold, request))
        bundle = current_bundle(
            _OPERATING_BUNDLE_KEY,
            _OPERATING_SOURCE_KEY,
            source_fingerprint,
            request,
            manifest,
        )
        if bundle is not None:
            render_download_controls(bundle, key_prefix="cost_operating_download")

    with procurement_tab:
        submitted, raw_request = render_procurement_form(gold)
        if submitted:
            submit_report(
                raw_request=raw_request,
                gold=gold,
                manifest=manifest,
                domains=domains,
                temp_root=temp_root,
                request_key=_PROCUREMENT_REQUEST_KEY,
                bundle_key=_PROCUREMENT_BUNDLE_KEY,
                source_key=_PROCUREMENT_SOURCE_KEY,
                source_fingerprint=source_fingerprint,
            )
        request = validated_session_request(
            _PROCUREMENT_REQUEST_KEY,
            _PROCUREMENT_BUNDLE_KEY,
            _PROCUREMENT_SOURCE_KEY,
            "procurement",
            domains,
        )
        render_procurement_view(prepare_procurement_dashboard(gold, request))
        bundle = current_bundle(
            _PROCUREMENT_BUNDLE_KEY,
            _PROCUREMENT_SOURCE_KEY,
            source_fingerprint,
            request,
            manifest,
        )
        if bundle is not None:
            render_download_controls(bundle, key_prefix="cost_procurement_download")


__all__ = ["render_cost_analysis_page"]
