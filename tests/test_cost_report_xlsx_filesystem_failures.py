from __future__ import annotations

import json
from pathlib import Path
from types import SimpleNamespace

import pytest

import agriinsight.cost_report_xlsx as xlsx
from agriinsight.cost_report_contract import (
    CostReportDomains,
    CostReportMetadata,
    CostReportRequest,
    ExportUnavailable,
)
from agriinsight.cost_report_data import prepare_cost_report


def _inputs(report_sources, tmp_path: Path):
    gold, manifest = report_sources
    request = CostReportRequest.from_mapping({}, CostReportDomains.from_gold(gold))
    metadata = CostReportMetadata.from_manifest(manifest, request)
    report = prepare_cost_report(gold, request, metadata)
    node = tmp_path / "node"
    node.write_bytes(b"runtime")
    node_modules = tmp_path / "node_modules"
    node_modules.mkdir()
    builder = tmp_path / "builder.mjs"
    builder.write_text("// fake builder", encoding="utf-8")
    runtime = xlsx.XlsxRuntime(node.resolve(), node_modules.resolve())
    temp_root = (tmp_path / "report-temp").resolve()
    return report, request, metadata, runtime, temp_root, builder.resolve()


def _render(inputs) -> bytes:
    report, request, metadata, runtime, temp_root, builder = inputs
    return xlsx.render_cost_report_xlsx(
        report, request, metadata, runtime, temp_root, builder
    )


def test_temp_directory_failure_is_typed(report_sources, tmp_path, monkeypatch) -> None:
    inputs = _inputs(report_sources, tmp_path)

    def fail_temp_directory(*args, **kwargs):
        raise OSError("temp denied")

    monkeypatch.setattr(xlsx, "TemporaryDirectory", fail_temp_directory)

    with pytest.raises(ExportUnavailable, match="filesystem operation failed.*temp denied"):
        _render(inputs)


def test_payload_write_failure_is_typed(report_sources, tmp_path, monkeypatch) -> None:
    inputs = _inputs(report_sources, tmp_path)
    original_write_text = Path.write_text

    def fail_payload(self: Path, *args, **kwargs):
        if self.name == "payload.json":
            raise OSError("payload denied")
        return original_write_text(self, *args, **kwargs)

    monkeypatch.setattr(xlsx, "_create_module_link", lambda *args: None)
    monkeypatch.setattr(Path, "write_text", fail_payload)

    with pytest.raises(ExportUnavailable, match="filesystem operation failed.*payload denied"):
        _render(inputs)


def test_output_read_failure_is_typed(report_sources, tmp_path, monkeypatch) -> None:
    inputs = _inputs(report_sources, tmp_path)
    original_open = Path.open

    def fake_run(command, **kwargs):
        Path(command[3]).write_bytes(b"xlsx")
        summary = {
            "ok": True,
            "sheets": xlsx._EXPECTED_SHEETS,
            "modelStatus": "PASS",
            "formulaErrorMatches": 0,
            "previews": len(xlsx._EXPECTED_SHEETS),
            "outputBytes": 4,
        }
        kwargs["stdout"].write(json.dumps(summary).encode("utf-8"))
        return SimpleNamespace(returncode=0)

    def fail_output_read(self: Path, mode="r", *args, **kwargs):
        if self.name == "cost-report.xlsx" and mode == "rb":
            raise OSError("output denied")
        return original_open(self, mode, *args, **kwargs)

    monkeypatch.setattr(xlsx, "_create_module_link", lambda *args: None)
    monkeypatch.setattr(xlsx.subprocess, "run", fake_run)
    monkeypatch.setattr(Path, "open", fail_output_read)

    with pytest.raises(ExportUnavailable, match="filesystem operation failed.*output denied"):
        _render(inputs)
