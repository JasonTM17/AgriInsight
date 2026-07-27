from __future__ import annotations

from hashlib import sha256

import pytest

import agriinsight.cost_report_single_export as single_export
from agriinsight.cost_report_contract import ExportUnavailable, ReportValidationError


def _fail_pdf(*args: object) -> bytes:
    raise AssertionError("The PDF renderer must not run for another format")


def _fail_xlsx_runtime() -> object:
    raise AssertionError("The XLSX runtime must not be initialized for another format")


def _fail_xlsx(*args: object) -> bytes:
    raise AssertionError("The XLSX renderer must not run for another format")


def test_csv_request_renders_only_csv(report_sources, monkeypatch, tmp_path) -> None:
    gold, manifest = report_sources
    monkeypatch.setattr(single_export, "render_cost_report_pdf", _fail_pdf)
    monkeypatch.setattr(single_export, "detect_xlsx_runtime", _fail_xlsx_runtime)
    monkeypatch.setattr(single_export, "render_cost_report_xlsx", _fail_xlsx)

    export = single_export.render_single_cost_export(
        gold,
        manifest,
        {},
        export_format="csv",
        temp_root=tmp_path / "_tmp",
    )

    assert export.export_format == "csv"
    assert export.artifact.filename.endswith(".csv")
    assert export.artifact.mime_type == "text/csv; charset=utf-8"
    assert export.artifact.content.startswith(b"\xef\xbb\xbf")
    assert export.byte_size == len(export.artifact.content)
    assert export.content_sha256 == sha256(export.artifact.content).hexdigest()


def test_pdf_request_never_touches_the_xlsx_runtime(
    report_sources, monkeypatch, tmp_path
) -> None:
    gold, manifest = report_sources
    monkeypatch.setattr(single_export, "render_cost_report_pdf", lambda *args: b"pdf")
    monkeypatch.setattr(single_export, "detect_xlsx_runtime", _fail_xlsx_runtime)
    monkeypatch.setattr(single_export, "render_cost_report_xlsx", _fail_xlsx)

    export = single_export.render_single_cost_export(
        gold,
        manifest,
        {},
        export_format="pdf",
        temp_root=tmp_path / "_tmp",
    )

    assert export.export_format == "pdf"
    assert export.artifact.content == b"pdf"
    assert export.artifact.mime_type == "application/pdf"


@pytest.mark.parametrize("requested", ["", "CSV.", "zip", "../csv", "json"])
def test_unknown_formats_are_refused(
    report_sources, requested, tmp_path
) -> None:
    gold, manifest = report_sources

    with pytest.raises(ReportValidationError, match="one of"):
        single_export.render_single_cost_export(
            gold,
            manifest,
            {},
            export_format=requested,
            temp_root=tmp_path / "_tmp",
        )


def test_non_string_format_is_refused(report_sources, tmp_path) -> None:
    gold, manifest = report_sources

    with pytest.raises(ReportValidationError, match="must be a string"):
        single_export.render_single_cost_export(
            gold,
            manifest,
            {},
            export_format=None,
            temp_root=tmp_path / "_tmp",
        )


def test_uppercase_and_padded_formats_are_normalized(
    report_sources, monkeypatch, tmp_path
) -> None:
    gold, manifest = report_sources
    monkeypatch.setattr(single_export, "detect_xlsx_runtime", _fail_xlsx_runtime)

    export = single_export.render_single_cost_export(
        gold,
        manifest,
        {},
        export_format="  CSV  ",
        temp_root=tmp_path / "_tmp",
    )

    assert export.export_format == "csv"


def test_oversized_export_is_refused_with_narrowing_guidance(
    report_sources, monkeypatch, tmp_path
) -> None:
    gold, manifest = report_sources
    monkeypatch.setattr(single_export, "MAX_BUNDLE_BYTES", 10)

    with pytest.raises(ReportValidationError, match="Narrow the filters"):
        single_export.render_single_cost_export(
            gold,
            manifest,
            {},
            export_format="csv",
            temp_root=tmp_path / "_tmp",
        )


def test_empty_render_is_refused(report_sources, monkeypatch, tmp_path) -> None:
    gold, manifest = report_sources
    monkeypatch.setattr(single_export, "render_cost_report_csv", lambda report: b"")

    with pytest.raises(ReportValidationError, match="empty file"):
        single_export.render_single_cost_export(
            gold,
            manifest,
            {},
            export_format="csv",
            temp_root=tmp_path / "_tmp",
        )


def test_safe_metadata_exposes_lineage_without_filesystem_paths(
    report_sources, monkeypatch, tmp_path
) -> None:
    gold, manifest = report_sources
    monkeypatch.setattr(single_export, "detect_xlsx_runtime", _fail_xlsx_runtime)

    export = single_export.render_single_cost_export(
        gold,
        manifest,
        {},
        export_format="csv",
        temp_root=tmp_path / "_tmp",
    )
    metadata = export.safe_metadata()

    assert set(metadata) == {
        "asOf",
        "byteSize",
        "checksumSha256",
        "contractVersion",
        "filename",
        "filterHash",
        "format",
        "rowCount",
        "runId",
    }
    assert metadata["rowCount"] >= 0
    rendered = repr(metadata)
    assert str(tmp_path) not in rendered
    for fragment in ("/", "\\", "_tmp", "artifacts"):
        assert fragment not in str(metadata["runId"])
    assert "_tmp" not in rendered


def test_xlsx_staging_must_be_an_absolute_tmp_directory(
    report_sources, monkeypatch, tmp_path
) -> None:
    gold, manifest = report_sources
    monkeypatch.setattr(single_export, "detect_xlsx_runtime", lambda: object())
    monkeypatch.setattr(single_export, "render_cost_report_xlsx", _fail_xlsx)

    with pytest.raises(ReportValidationError, match="absolute path"):
        single_export.render_single_cost_export(
            gold,
            manifest,
            {},
            export_format="xlsx",
            temp_root=tmp_path.relative_to(tmp_path.anchor),
        )

    with pytest.raises(ReportValidationError, match="_tmp"):
        single_export.render_single_cost_export(
            gold,
            manifest,
            {},
            export_format="xlsx",
            temp_root=tmp_path / "staging",
        )


def test_xlsx_request_stages_under_tmp_and_returns_spreadsheet(
    report_sources, monkeypatch, tmp_path
) -> None:
    gold, manifest = report_sources
    staging = tmp_path / "_tmp"
    seen: dict[str, object] = {}

    def _capture(report, request, metadata, runtime, temp_root, builder_path) -> bytes:
        seen["temp_root"] = temp_root
        return b"xlsx-bytes"

    monkeypatch.setattr(single_export, "detect_xlsx_runtime", lambda: object())
    monkeypatch.setattr(single_export, "render_cost_report_xlsx", _capture)
    monkeypatch.setattr(single_export, "render_cost_report_pdf", _fail_pdf)

    export = single_export.render_single_cost_export(
        gold,
        manifest,
        {},
        export_format="xlsx",
        temp_root=staging,
    )

    assert export.artifact.content == b"xlsx-bytes"
    assert export.artifact.filename.endswith(".xlsx")
    assert seen["temp_root"] == staging


def test_unavailable_xlsx_runtime_surfaces_to_the_caller(
    report_sources, monkeypatch, tmp_path
) -> None:
    gold, manifest = report_sources

    def _unavailable() -> object:
        raise ExportUnavailable("XLSX runtime is not provisioned")

    monkeypatch.setattr(single_export, "detect_xlsx_runtime", _unavailable)

    with pytest.raises(ExportUnavailable, match="not provisioned"):
        single_export.render_single_cost_export(
            gold,
            manifest,
            {},
            export_format="xlsx",
            temp_root=tmp_path / "_tmp",
        )
