"""Render exactly one requested cost report format.

The dashboard bundle path renders CSV, PDF and optional XLSX together, which is
correct for a local download button but wrong for an HTTP request: it pays for
formats nobody asked for and it initializes the XLSX runtime even for a CSV
download. This module reuses the same validation, preparation and renderers, but
dispatches a single format and reports only safe metadata.
"""

from __future__ import annotations

from collections.abc import Mapping
from dataclasses import dataclass
from hashlib import sha256
from pathlib import Path

import pandas as pd

from agriinsight.cost_report_assets import bundled_font_dir, bundled_xlsx_builder
from agriinsight.cost_report_contract import (
    MAX_BUNDLE_BYTES,
    CostReportDomains,
    CostReportMetadata,
    CostReportRequest,
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

EXPORT_FORMATS: tuple[str, ...] = ("csv", "pdf", "xlsx")
STAGING_DIRECTORY_NAME = "_tmp"

_MIME_TYPES: Mapping[str, str] = {
    "csv": "text/csv; charset=utf-8",
    "pdf": "application/pdf",
    "xlsx": "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
}


@dataclass(frozen=True, slots=True)
class SingleCostExport:
    """One rendered artifact plus metadata that is safe to send to a browser."""

    artifact: ReportArtifact
    export_format: str
    row_count: int
    run_id: str
    contract_version: str
    as_of_date: str
    filter_hash: str
    content_sha256: str

    @property
    def byte_size(self) -> int:
        return len(self.artifact.content)

    def safe_metadata(self) -> dict[str, object]:
        """Return lineage and sizing only. No filesystem path ever appears here."""

        return {
            "asOf": self.as_of_date,
            "byteSize": self.byte_size,
            "checksumSha256": self.content_sha256,
            "contractVersion": self.contract_version,
            "filename": self.artifact.filename,
            "filterHash": self.filter_hash,
            "format": self.export_format,
            "rowCount": self.row_count,
            "runId": self.run_id,
        }


def resolve_export_format(value: object) -> str:
    """Accept only an allowlisted format so no caller can select a renderer by path."""

    if not isinstance(value, str):
        raise ReportValidationError("The export format must be a string")
    normalized = value.strip().lower()
    if normalized not in EXPORT_FORMATS:
        raise ReportValidationError(
            "The export format must be one of " + ", ".join(EXPORT_FORMATS)
        )
    return normalized


def _checked_staging_root(temp_root: Path) -> Path:
    if not temp_root.is_absolute():
        raise ReportValidationError("The export staging root must be an absolute path")
    if STAGING_DIRECTORY_NAME not in temp_root.parts:
        raise ReportValidationError(
            "Export staging is restricted to a "
            f"{STAGING_DIRECTORY_NAME!r} directory"
        )
    return temp_root


def _row_count(report: PreparedCostReport) -> int:
    return int(len(report.cost_detail)) + int(len(report.procurement_detail))


def render_single_cost_export(
    gold: Mapping[str, pd.DataFrame],
    manifest: Mapping[str, object],
    raw_request: Mapping[str, object],
    *,
    export_format: object,
    temp_root: Path,
    font_dir: Path | None = None,
    xlsx_runtime: XlsxRuntime | None = None,
    xlsx_builder_path: Path | None = None,
) -> SingleCostExport:
    """Validate once, prepare once, then render only the requested format."""

    selected = resolve_export_format(export_format)
    request = CostReportRequest.from_mapping(
        raw_request, CostReportDomains.from_gold(gold)
    )
    metadata = CostReportMetadata.from_manifest(manifest, request)
    report = prepare_cost_report(gold, request, metadata)

    if selected == "csv":
        content = render_cost_report_csv(report)
    elif selected == "pdf":
        content = render_cost_report_pdf(
            report, request, metadata, font_dir or bundled_font_dir()
        )
    else:
        staging_root = _checked_staging_root(temp_root)
        content = render_cost_report_xlsx(
            report,
            request,
            metadata,
            xlsx_runtime or detect_xlsx_runtime(),
            staging_root,
            xlsx_builder_path or bundled_xlsx_builder(),
        )

    if not content:
        raise ReportValidationError(
            f"The {selected.upper()} renderer returned an empty file"
        )
    if len(content) > MAX_BUNDLE_BYTES:
        raise ReportValidationError(
            f"The {selected.upper()} export is {len(content):,} bytes, which exceeds "
            f"the {MAX_BUNDLE_BYTES:,}-byte download limit. Narrow the filters."
        )

    artifact = ReportArtifact(
        filename=metadata.filename(request, selected),
        mime_type=_MIME_TYPES[selected],
        content=content,
    )
    return SingleCostExport(
        artifact=artifact,
        export_format=selected,
        row_count=_row_count(report),
        run_id=metadata.run_id,
        contract_version=metadata.export_version,
        as_of_date=metadata.as_of_date,
        filter_hash=metadata.filter_hash,
        content_sha256=sha256(content).hexdigest(),
    )


__all__ = [
    "EXPORT_FORMATS",
    "SingleCostExport",
    "render_single_cost_export",
    "resolve_export_format",
]
