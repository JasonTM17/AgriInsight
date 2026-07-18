from __future__ import annotations

import shutil
from pathlib import Path

import pytest

from agriinsight.cost_report_contract import (
    CostReportDomains,
    CostReportMetadata,
    CostReportRequest,
    ExportUnavailable,
)
from agriinsight.cost_report_data import prepare_cost_report
from agriinsight.cost_report_xlsx import (
    XlsxRuntime,
    detect_xlsx_runtime,
    render_cost_report_xlsx,
)


def _prepared_report(gold, manifest):
    request = CostReportRequest.from_mapping({}, CostReportDomains.from_gold(gold))
    metadata = CostReportMetadata.from_manifest(manifest, request)
    return request, metadata, prepare_cost_report(gold, request, metadata)


def test_xlsx_runtime_requires_both_explicit_environment_paths() -> None:
    with pytest.raises(ExportUnavailable, match="AGRIINSIGHT_NODE_EXECUTABLE"):
        detect_xlsx_runtime({})


@pytest.mark.skipif(shutil.which("node") is None, reason="Node runtime not available")
def test_xlsx_adapter_escapes_formulas_and_cleans_temp_on_success_and_failure(
    report_sources,
    tmp_path,
) -> None:
    original_gold, manifest = report_sources
    gold = dict(original_gold)
    detail = original_gold["cost_activity_detail"].copy()
    detail.loc[0, "notes"] = "=SUM(A1:A2)"
    gold["cost_activity_detail"] = detail
    request, metadata, report = _prepared_report(gold, manifest)

    node_modules = tmp_path / "runtime-node-modules"
    node_modules.mkdir()
    runtime = XlsxRuntime(
        node_executable=Path(shutil.which("node") or "").resolve(),
        node_modules=node_modules.resolve(),
    )
    temp_root = (tmp_path / "report-temp").resolve()
    builder = tmp_path / "fake-builder.mjs"
    builder.write_text(
        """
import { mkdir, readFile, writeFile } from "node:fs/promises";
const payload = JSON.parse(await readFile(process.argv[2], "utf8"));
const safe = payload.report.costDetail.records.some(
  (row) => row.notes === "'=SUM(A1:A2)",
);
if (!safe) throw new Error("formula-like text was not escaped");
await mkdir(process.argv[4], { recursive: true });
await writeFile(process.argv[3], Buffer.from("xlsx"));
process.stdout.write(JSON.stringify({
  ok: true,
  sheets: ["Summary", "Monthly", "Cost Detail", "Procurement Detail", "Checks", "Metadata"],
  modelStatus: "PASS",
  formulaErrorMatches: 0,
  previews: 6,
  outputBytes: 4,
}));
""".strip(),
        encoding="utf-8",
    )

    assert render_cost_report_xlsx(
        report,
        request,
        metadata,
        runtime,
        temp_root,
        builder.resolve(),
    ) == b"xlsx"
    assert list(temp_root.iterdir()) == []

    failing_builder = tmp_path / "failing-builder.mjs"
    failing_builder.write_text(
        'process.stderr.write("expected failure"); process.exit(7);',
        encoding="utf-8",
    )
    with pytest.raises(ExportUnavailable, match="exit code 7.*expected failure"):
        render_cost_report_xlsx(
            report,
            request,
            metadata,
            runtime,
            temp_root,
            failing_builder.resolve(),
        )
    assert list(temp_root.iterdir()) == []

    missing_qa_builder = tmp_path / "missing-qa-builder.mjs"
    missing_qa_builder.write_text(
        'import { writeFile } from "node:fs/promises"; '
        'await writeFile(process.argv[3], Buffer.from("xlsx"));',
        encoding="utf-8",
    )
    with pytest.raises(ExportUnavailable, match="no QA summary"):
        render_cost_report_xlsx(
            report,
            request,
            metadata,
            runtime,
            temp_root,
            missing_qa_builder.resolve(),
        )
    assert list(temp_root.iterdir()) == []
