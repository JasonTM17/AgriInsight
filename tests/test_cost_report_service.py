from __future__ import annotations

import pytest

import agriinsight.cost_report_service as service
from agriinsight.cost_report_contract import ExportUnavailable, ReportValidationError


def _disable_xlsx() -> None:
    raise ExportUnavailable("XLSX runtime is not provisioned")


def test_service_returns_csv_pdf_and_explicit_xlsx_unavailable_reason(
    report_sources,
    monkeypatch,
    tmp_path,
) -> None:
    gold, manifest = report_sources
    monkeypatch.setattr(service, "render_cost_report_pdf", lambda *args: b"pdf")
    monkeypatch.setattr(service, "detect_xlsx_runtime", _disable_xlsx)

    bundle = service.build_cost_report_bundle(
        gold,
        manifest,
        {},
        temp_root=tmp_path,
    )

    assert bundle.csv.filename.endswith(".csv")
    assert bundle.csv.content.startswith(b"\xef\xbb\xbf")
    assert bundle.pdf.content == b"pdf"
    assert bundle.xlsx is None
    assert bundle.xlsx_unavailable_reason == "XLSX runtime is not provisioned"


def test_service_enforces_complete_bundle_byte_limit(
    report_sources,
    monkeypatch,
    tmp_path,
) -> None:
    gold, manifest = report_sources
    monkeypatch.setattr(service, "MAX_BUNDLE_BYTES", 10)
    monkeypatch.setattr(
        service,
        "render_cost_report_pdf",
        lambda *args: b"x" * 10,
    )
    monkeypatch.setattr(service, "detect_xlsx_runtime", _disable_xlsx)

    with pytest.raises(ReportValidationError, match="complete report bundle"):
        service.build_cost_report_bundle(
            gold,
            manifest,
            {},
            temp_root=tmp_path,
        )


def test_service_keeps_csv_and_pdf_when_xlsx_adapter_is_unavailable(
    report_sources,
    monkeypatch,
    tmp_path,
) -> None:
    gold, manifest = report_sources
    monkeypatch.setattr(service, "render_cost_report_pdf", lambda *args: b"pdf")
    monkeypatch.setattr(service, "detect_xlsx_runtime", lambda: object())
    monkeypatch.setattr(
        service,
        "render_cost_report_xlsx",
        lambda *args: (_ for _ in ()).throw(ExportUnavailable("XLSX filesystem failed")),
    )

    bundle = service.build_cost_report_bundle(
        gold,
        manifest,
        {},
        temp_root=tmp_path,
    )

    assert bundle.csv.content
    assert bundle.pdf.content == b"pdf"
    assert bundle.xlsx is None
    assert bundle.xlsx_unavailable_reason == "XLSX filesystem failed"
