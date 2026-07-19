from __future__ import annotations

import importlib.util
import io

import pytest

from agriinsight.cost_report_contract import (
    CostReportDomains,
    CostReportMetadata,
    CostReportRequest,
    ExportUnavailable,
)
from agriinsight.cost_report_data import prepare_cost_report
from agriinsight.cost_report_pdf import render_cost_report_pdf


_PDF_STACK_AVAILABLE = all(
    importlib.util.find_spec(module) is not None for module in ("reportlab", "pypdf")
)


def _request_and_metadata(gold, manifest, raw=None):
    request = CostReportRequest.from_mapping(
        raw or {}, CostReportDomains.from_gold(gold)
    )
    return request, CostReportMetadata.from_manifest(manifest, request)


@pytest.mark.skipif(not _PDF_STACK_AVAILABLE, reason="PDF report extras not installed")
def test_pdf_is_deterministic_and_contains_traceable_vietnamese_text(
    report_sources,
) -> None:
    from pypdf import PdfReader

    gold, manifest = report_sources
    request, metadata = _request_and_metadata(gold, manifest)
    report = prepare_cost_report(gold, request, metadata)

    first = render_cost_report_pdf(report, request, metadata)
    second = render_cost_report_pdf(report, request, metadata)
    reader = PdfReader(io.BytesIO(first))
    page_texts = [(page.extract_text() or "").strip() for page in reader.pages]
    extracted = "\n".join(page_texts)

    assert first == second
    assert len(reader.pages) >= 2
    assert "BÁO CÁO PHÂN TÍCH CHI PHÍ" in extracted
    assert "Tổng hợp = chi tiết mua hàng" in extracted
    assert "MODEL STATUS: PASS" in extracted
    assert manifest["run_id"] in extracted
    assert all(len(text) > 200 for text in page_texts)


@pytest.mark.skipif(not _PDF_STACK_AVAILABLE, reason="PDF report extras not installed")
def test_pdf_includes_operating_season_filter(report_sources) -> None:
    from pypdf import PdfReader

    gold, manifest = report_sources
    season = sorted(CostReportDomains.from_gold(gold).seasons)[0]
    request, metadata = _request_and_metadata(
        gold,
        manifest,
        {"scope": "operating", "season": season},
    )
    report = prepare_cost_report(gold, request, metadata)

    reader = PdfReader(io.BytesIO(render_cost_report_pdf(report, request, metadata)))
    extracted = "\n".join((page.extract_text() or "") for page in reader.pages)

    assert "Mùa vụ" in extracted
    assert season in extracted


@pytest.mark.skipif(not _PDF_STACK_AVAILABLE, reason="PDF report extras not installed")
def test_pdf_requires_bundled_font_and_license_assets(report_sources, tmp_path) -> None:
    gold, manifest = report_sources
    request, metadata = _request_and_metadata(gold, manifest)
    report = prepare_cost_report(gold, request, metadata)

    with pytest.raises(ExportUnavailable, match="Noto Sans"):
        render_cost_report_pdf(report, request, metadata, tmp_path)
