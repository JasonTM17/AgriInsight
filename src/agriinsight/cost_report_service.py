from __future__ import annotations

from collections.abc import Mapping
from pathlib import Path

import pandas as pd

from agriinsight.cost_report_assets import bundled_font_dir, bundled_xlsx_builder
from agriinsight.cost_report_contract import (
    MAX_BUNDLE_BYTES,
    CostReportBundle,
    CostReportDomains,
    CostReportMetadata,
    CostReportRequest,
    ExportUnavailable,
    PreparedCostReport,
    ReportArtifact,
    ReportValidationError,
)
from agriinsight.cost_report_csv import render_cost_report_csv
from agriinsight.cost_report_data import prepare_cost_report
from agriinsight.cost_report_pdf import render_cost_report_pdf
from agriinsight.cost_report_xlsx import (
    XlsxRuntime,
    detect_xlsx_runtime,
    render_cost_report_xlsx,
)


_CSV_MIME = "text/csv; charset=utf-8"
_PDF_MIME = "application/pdf"
_XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
def _artifact(
    metadata: CostReportMetadata,
    request: CostReportRequest,
    extension: str,
    mime_type: str,
    content: bytes,
) -> ReportArtifact:
    if not content:
        raise ReportValidationError(f"The {extension.upper()} renderer returned an empty file")
    return ReportArtifact(
        filename=metadata.filename(request, extension),
        mime_type=mime_type,
        content=content,
    )


def _validate_bundle_size(artifacts: tuple[ReportArtifact, ...]) -> None:
    total_bytes = sum(len(artifact.content) for artifact in artifacts)
    if total_bytes > MAX_BUNDLE_BYTES:
        raise ReportValidationError(
            "The complete report bundle exceeds the "
            f"{MAX_BUNDLE_BYTES:,}-byte download limit"
        )


def _optional_xlsx_artifact(
    report: PreparedCostReport,
    request: CostReportRequest,
    metadata: CostReportMetadata,
    *,
    runtime: XlsxRuntime | None,
    temp_root: Path,
    builder_path: Path,
) -> tuple[ReportArtifact | None, str | None]:
    try:
        checked_runtime = runtime or detect_xlsx_runtime()
        content = render_cost_report_xlsx(
            report,
            request,
            metadata,
            checked_runtime,
            temp_root,
            builder_path,
        )
    except ExportUnavailable as error:
        return None, str(error)
    return _artifact(metadata, request, "xlsx", _XLSX_MIME, content), None


def build_cost_report_bundle(
    gold: Mapping[str, pd.DataFrame],
    manifest: Mapping[str, object],
    raw_request: Mapping[str, object],
    *,
    font_dir: Path | None = None,
    temp_root: Path,
    xlsx_runtime: XlsxRuntime | None = None,
    xlsx_builder_path: Path | None = None,
) -> CostReportBundle:
    """Validate one request and render a bounded in-memory report bundle."""

    request = CostReportRequest.from_mapping(raw_request, CostReportDomains.from_gold(gold))
    metadata = CostReportMetadata.from_manifest(manifest, request)
    report = prepare_cost_report(gold, request, metadata)
    csv_artifact = _artifact(
        metadata,
        request,
        "csv",
        _CSV_MIME,
        render_cost_report_csv(report),
    )
    pdf_artifact = _artifact(
        metadata,
        request,
        "pdf",
        _PDF_MIME,
        render_cost_report_pdf(report, request, metadata, font_dir or bundled_font_dir()),
    )
    xlsx_artifact, xlsx_reason = _optional_xlsx_artifact(
        report,
        request,
        metadata,
        runtime=xlsx_runtime,
        temp_root=temp_root,
        builder_path=xlsx_builder_path or bundled_xlsx_builder(),
    )
    artifacts = (csv_artifact, pdf_artifact)
    if xlsx_artifact is not None:
        artifacts += (xlsx_artifact,)
    _validate_bundle_size(artifacts)
    return CostReportBundle(
        request=request,
        metadata=metadata,
        report=report,
        csv=csv_artifact,
        pdf=pdf_artifact,
        xlsx=xlsx_artifact,
        xlsx_unavailable_reason=xlsx_reason,
    )


__all__ = ["build_cost_report_bundle"]
