from __future__ import annotations

import json
import os
import subprocess
from collections.abc import Mapping
from dataclasses import dataclass
from pathlib import Path
from tempfile import TemporaryDirectory

import pandas as pd

from agriinsight.cost_report_contract import (
    MAX_BUNDLE_BYTES,
    CostReportMetadata,
    CostReportRequest,
    ExportUnavailable,
    PreparedCostReport,
)
from agriinsight.cost_report_csv import escape_spreadsheet_text, escaped_frame


_NODE_EXECUTABLE_ENV = "AGRIINSIGHT_NODE_EXECUTABLE"
_NODE_MODULES_ENV = "AGRIINSIGHT_NODE_MODULES"
_BUILD_TIMEOUT_SECONDS = 180
_JUNCTION_TIMEOUT_SECONDS = 15
_MAX_ERROR_CHARACTERS = 4_000
_EXPECTED_SHEETS = [
    "Summary",
    "Monthly",
    "Cost Detail",
    "Procurement Detail",
    "Checks",
    "Metadata",
]


@dataclass(frozen=True, slots=True)
class XlsxRuntime:
    """Explicitly provisioned Node and artifact-tool dependency paths."""

    node_executable: Path
    node_modules: Path

    @classmethod
    def from_environment(
        cls, environ: Mapping[str, str] | None = None
    ) -> XlsxRuntime:
        source = os.environ if environ is None else environ
        node_value = source.get(_NODE_EXECUTABLE_ENV, "").strip()
        modules_value = source.get(_NODE_MODULES_ENV, "").strip()
        missing = [
            name
            for name, value in (
                (_NODE_EXECUTABLE_ENV, node_value),
                (_NODE_MODULES_ENV, modules_value),
            )
            if not value
        ]
        if missing:
            raise ExportUnavailable(
                "XLSX export requires both explicit runtime variables; missing "
                + ", ".join(missing)
            )
        return _validated_runtime(cls(Path(node_value), Path(modules_value)))


def detect_xlsx_runtime(environ: Mapping[str, str] | None = None) -> XlsxRuntime:
    """Detect XLSX capability only from the two documented environment variables."""

    return XlsxRuntime.from_environment(environ)


def _absolute_existing_path(path: Path, label: str, *, directory: bool) -> Path:
    if not path.is_absolute():
        raise ExportUnavailable(f"{label} must be an absolute path")
    try:
        resolved = path.resolve(strict=True)
    except OSError as error:
        raise ExportUnavailable(f"{label} does not exist: {path}") from error
    expected_type = resolved.is_dir() if directory else resolved.is_file()
    if not expected_type:
        kind = "directory" if directory else "file"
        raise ExportUnavailable(f"{label} must reference a {kind}: {resolved}")
    return resolved


def _validated_runtime(runtime: XlsxRuntime) -> XlsxRuntime:
    return XlsxRuntime(
        node_executable=_absolute_existing_path(
            runtime.node_executable, _NODE_EXECUTABLE_ENV, directory=False
        ),
        node_modules=_absolute_existing_path(
            runtime.node_modules, _NODE_MODULES_ENV, directory=True
        ),
    )


def _frame_payload(frame: pd.DataFrame) -> dict[str, object]:
    safe_frame = escaped_frame(frame)
    records = json.loads(
        safe_frame.to_json(
            orient="records",
            date_format="iso",
            date_unit="ms",
            force_ascii=False,
        )
    )
    return {
        "columns": [str(column) for column in safe_frame.columns],
        "records": records,
    }


def _safe_mapping_values(values: Mapping[str, object]) -> dict[str, object]:
    return {key: escape_spreadsheet_text(value) for key, value in values.items()}


def _build_payload(
    report: PreparedCostReport,
    request: CostReportRequest,
    metadata: CostReportMetadata,
) -> dict[str, object]:
    return {
        "schemaVersion": 1,
        "request": _safe_mapping_values(request.canonical_dict()),
        "metadata": _safe_mapping_values(
            {
                "run_id": metadata.run_id,
                "as_of_date": metadata.as_of_date,
                "source_pipeline": metadata.source_pipeline,
                "filter_hash": metadata.filter_hash,
                "export_version": metadata.export_version,
            }
        ),
        "report": {
            "summary": _frame_payload(report.summary),
            "monthly": _frame_payload(report.monthly),
            "costDetail": _frame_payload(report.cost_detail),
            "procurementDetail": _frame_payload(report.procurement_detail),
            "checks": _frame_payload(report.checks),
            "metadata": _frame_payload(report.metadata),
        },
    }


def _bounded_message(value: object) -> str:
    if value is None:
        return ""
    if isinstance(value, bytes):
        text = value.decode("utf-8", errors="replace")
    else:
        text = str(value)
    compact = " ".join(text.split())
    if len(compact) <= _MAX_ERROR_CHARACTERS:
        return compact
    return f"...{compact[-_MAX_ERROR_CHARACTERS:]}"


def _bounded_file_message(path: Path) -> str:
    try:
        with path.open("rb") as message_file:
            message_file.seek(0, os.SEEK_END)
            size = message_file.tell()
            message_file.seek(max(0, size - (_MAX_ERROR_CHARACTERS * 4)))
            return _bounded_message(message_file.read())
    except OSError:
        return ""


def _read_qa_output(path: Path) -> str:
    try:
        with path.open("rb") as qa_file:
            qa_file.seek(0, os.SEEK_END)
            size = qa_file.tell()
            qa_file.seek(max(0, size - 64_000))
            return qa_file.read().decode("utf-8", errors="replace")
    except OSError as error:
        raise ExportUnavailable("Unable to read artifact-tool QA summary") from error


def _create_module_link(link_path: Path, node_modules: Path) -> None:
    try:
        os.symlink(node_modules, link_path, target_is_directory=True)
        return
    except OSError as symlink_error:
        if os.name != "nt":
            raise ExportUnavailable(
                f"Unable to link the provisioned node_modules directory: {symlink_error}"
            ) from symlink_error

    try:
        junction = subprocess.run(
            ["cmd.exe", "/d", "/c", "mklink", "/J", str(link_path), str(node_modules)],
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace",
            timeout=_JUNCTION_TIMEOUT_SECONDS,
            check=False,
        )
    except (OSError, subprocess.TimeoutExpired) as error:
        raise ExportUnavailable(
            f"Unable to create a temporary node_modules junction: {error}"
        ) from error
    if junction.returncode != 0 or not os.path.lexists(link_path):
        detail = _bounded_message(junction.stderr) or _bounded_message(junction.stdout)
        raise ExportUnavailable(
            "Unable to create a temporary node_modules junction"
            + (f": {detail}" if detail else "")
        )


def _remove_module_link(link_path: Path) -> None:
    if not os.path.lexists(link_path):
        return
    try:
        link_path.unlink()
    except (IsADirectoryError, PermissionError):
        os.rmdir(link_path)


def _read_bounded_output(output_path: Path) -> bytes:
    if not output_path.is_file():
        raise ExportUnavailable("The artifact-tool builder did not create an XLSX file")
    with output_path.open("rb") as output_file:
        content = output_file.read(MAX_BUNDLE_BYTES + 1)
    if not content:
        raise ExportUnavailable("The artifact-tool builder created an empty XLSX file")
    if len(content) > MAX_BUNDLE_BYTES:
        raise ExportUnavailable(
            f"The XLSX export exceeds the {MAX_BUNDLE_BYTES:,}-byte bundle limit"
        )
    return content


def _validate_qa_summary(stdout: str) -> int:
    lines = [line.strip() for line in stdout.splitlines() if line.strip()]
    if not lines:
        raise ExportUnavailable("artifact-tool builder returned no QA summary")
    try:
        summary = json.loads(lines[-1])
    except json.JSONDecodeError as error:
        raise ExportUnavailable("artifact-tool builder returned invalid QA JSON") from error
    expected = {
        "ok": True,
        "sheets": _EXPECTED_SHEETS,
        "modelStatus": "PASS",
        "formulaErrorMatches": 0,
        "previews": len(_EXPECTED_SHEETS),
    }
    mismatches = [
        key for key, value in expected.items() if summary.get(key) != value
    ]
    if mismatches:
        raise ExportUnavailable(
            "artifact-tool QA summary failed required fields: "
            + ", ".join(mismatches)
        )
    output_bytes = summary.get("outputBytes")
    if (
        isinstance(output_bytes, bool)
        or not isinstance(output_bytes, int)
        or output_bytes <= 0
    ):
        raise ExportUnavailable("artifact-tool QA summary has invalid outputBytes")
    return output_bytes


def _render_cost_report_xlsx(
    report: PreparedCostReport,
    request: CostReportRequest,
    metadata: CostReportMetadata,
    runtime: XlsxRuntime,
    temp_root: Path,
    builder_path: Path,
) -> bytes:
    """Build and return one in-memory workbook without exposing filesystem paths."""

    checked_runtime = _validated_runtime(runtime)
    checked_builder = _absolute_existing_path(
        builder_path, "XLSX builder", directory=False
    )
    if not temp_root.is_absolute():
        raise ExportUnavailable("XLSX temp_root must be an absolute path")
    try:
        temp_root.mkdir(parents=True, exist_ok=True)
        checked_temp_root = temp_root.resolve(strict=True)
    except OSError as error:
        raise ExportUnavailable(f"Unable to prepare XLSX temp_root: {temp_root}") from error
    if not checked_temp_root.is_dir():
        raise ExportUnavailable(f"XLSX temp_root must be a directory: {checked_temp_root}")

    payload = _build_payload(report, request, metadata)
    with TemporaryDirectory(prefix="cost-report-", dir=checked_temp_root) as temp_name:
        working_directory = Path(temp_name)
        module_link = working_directory / "node_modules"
        payload_path = working_directory / "payload.json"
        output_path = working_directory / "cost-report.xlsx"
        preview_directory = working_directory / "previews"
        stdout_path = working_directory / "builder-stdout.log"
        stderr_path = working_directory / "builder-stderr.log"
        try:
            _create_module_link(module_link, checked_runtime.node_modules)
            payload_path.write_text(
                json.dumps(
                    payload,
                    ensure_ascii=False,
                    allow_nan=False,
                    separators=(",", ":"),
                ),
                encoding="utf-8",
            )
            try:
                with stdout_path.open("wb") as stdout_file, stderr_path.open(
                    "wb"
                ) as stderr_file:
                    result = subprocess.run(
                        [
                            str(checked_runtime.node_executable),
                            str(checked_builder),
                            str(payload_path),
                            str(output_path),
                            str(preview_directory),
                        ],
                        cwd=working_directory,
                        stdout=stdout_file,
                        stderr=stderr_file,
                        timeout=_BUILD_TIMEOUT_SECONDS,
                        check=False,
                    )
            except subprocess.TimeoutExpired as error:
                detail = _bounded_file_message(stderr_path) or _bounded_file_message(
                    stdout_path
                )
                raise ExportUnavailable(
                    f"XLSX export timed out after {_BUILD_TIMEOUT_SECONDS} seconds"
                    + (f": {detail}" if detail else "")
                ) from error
            except OSError as error:
                raise ExportUnavailable(
                    f"Unable to start the explicit Node runtime: {error}"
                ) from error

            if result.returncode != 0:
                detail = _bounded_file_message(stderr_path) or _bounded_file_message(
                    stdout_path
                )
                raise ExportUnavailable(
                    f"artifact-tool XLSX builder failed with exit code {result.returncode}"
                    + (f": {detail}" if detail else "")
                )
            expected_bytes = _validate_qa_summary(_read_qa_output(stdout_path))
            content = _read_bounded_output(output_path)
            if len(content) != expected_bytes:
                raise ExportUnavailable(
                    "artifact-tool QA outputBytes does not match the XLSX file"
                )
            return content
        finally:
            _remove_module_link(module_link)


def render_cost_report_xlsx(
    report: PreparedCostReport,
    request: CostReportRequest,
    metadata: CostReportMetadata,
    runtime: XlsxRuntime,
    temp_root: Path,
    builder_path: Path,
) -> bytes:
    """Build one workbook while normalizing filesystem failures for optional fallback."""

    try:
        return _render_cost_report_xlsx(
            report,
            request,
            metadata,
            runtime,
            temp_root,
            builder_path,
        )
    except ExportUnavailable:
        raise
    except OSError as error:
        detail = _bounded_message(error)
        raise ExportUnavailable(
            "XLSX filesystem operation failed" + (f": {detail}" if detail else "")
        ) from error
