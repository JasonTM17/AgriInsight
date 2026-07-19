from __future__ import annotations

import logging
from collections.abc import Mapping
from pathlib import Path

import pandas as pd
import streamlit as st

from agriinsight import cost_report_service
from agriinsight.cost_report_contract import (
    CostReportBundle,
    CostReportDomains,
    CostReportRequest,
    ExportUnavailable,
    ReportValidationError,
)


_LOGGER = logging.getLogger(__name__)


def validated_session_request(
    request_key: str,
    bundle_key: str,
    source_key: str,
    scope: str,
    domains: CostReportDomains,
) -> CostReportRequest:
    request = st.session_state.get(request_key)
    if isinstance(request, CostReportRequest):
        raw = {
            key: value
            for key, value in request.canonical_dict().items()
            if value is not None
        }
        try:
            return CostReportRequest.from_mapping(raw, domains)
        except ReportValidationError:
            st.session_state.pop(request_key, None)
            st.session_state.pop(bundle_key, None)
            st.session_state.pop(source_key, None)
    return CostReportRequest.from_mapping({"scope": scope}, domains)


def current_bundle(
    bundle_key: str,
    source_key: str,
    source_fingerprint: str,
    request: CostReportRequest,
    manifest: Mapping[str, object],
) -> CostReportBundle | None:
    bundle = st.session_state.get(bundle_key)
    if bundle is None:
        return None
    metadata = getattr(bundle, "metadata", None)
    is_current = (
        getattr(bundle, "request", None) == request
        and st.session_state.get(source_key) == source_fingerprint
        and getattr(metadata, "run_id", None) == manifest.get("run_id")
        and getattr(metadata, "as_of_date", None) == manifest.get("as_of_date")
        and getattr(metadata, "source_pipeline", None) == manifest.get("pipeline")
    )
    if not is_current:
        st.session_state.pop(bundle_key, None)
        st.session_state.pop(source_key, None)
        return None
    return bundle


def submit_report(
    *,
    raw_request: Mapping[str, object],
    gold: Mapping[str, pd.DataFrame],
    manifest: Mapping[str, object],
    domains: CostReportDomains,
    temp_root: Path,
    request_key: str,
    bundle_key: str,
    source_key: str,
    source_fingerprint: str,
) -> None:
    st.session_state.pop(bundle_key, None)
    st.session_state.pop(source_key, None)
    try:
        request = CostReportRequest.from_mapping(raw_request, domains)
        st.session_state[request_key] = request
        bundle = cost_report_service.build_cost_report_bundle(
            gold,
            manifest,
            raw_request,
            temp_root=temp_root,
        )
        if bundle.xlsx is None and bundle.xlsx_unavailable_reason:
            _LOGGER.warning("XLSX export unavailable: %s", bundle.xlsx_unavailable_reason)
        st.session_state[bundle_key] = bundle
        st.session_state[source_key] = source_fingerprint
    except ReportValidationError as error:
        st.error(f"Không thể tạo report: {error}")
    except ExportUnavailable:
        _LOGGER.exception("Cost report runtime is unavailable")
        st.error(
            "Không thể tạo report vì runtime xuất báo cáo chưa sẵn sàng. "
            "Vui lòng liên hệ quản trị hệ thống."
        )


__all__ = [
    "current_bundle",
    "submit_report",
    "validated_session_request",
]
