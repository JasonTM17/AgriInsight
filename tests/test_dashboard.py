from __future__ import annotations

import hashlib
import json
import shutil
from collections.abc import Sequence
from datetime import date
from pathlib import Path
from types import SimpleNamespace

import pandas as pd
import pytest
from streamlit.testing.v1 import AppTest

from agriinsight.config import GenerationConfig
from agriinsight.cost_report_contract import (
    CostReportDomains,
    CostReportMetadata,
    CostReportRequest,
    ExportUnavailable,
    ReportArtifact,
    ReportValidationError,
)
from agriinsight.pipeline import run_pipeline


@pytest.fixture(scope="session")
def dashboard_artifact_root(tmp_path_factory: pytest.TempPathFactory) -> Path:
    artifact_root = tmp_path_factory.mktemp("dashboard") / "artifacts"
    run_pipeline(
        artifact_root,
        GenerationConfig(
            seed=7,
            as_of_date=date(2026, 7, 18),
            farm_count=2,
            fields_per_farm=2,
            activities_per_season=6,
            material_count=5,
            sensor_history_days=14,
            sensor_readings_per_day=1,
        ),
    )
    return artifact_root


def _run_dashboard(artifact_root: Path, monkeypatch) -> AppTest:
    monkeypatch.setenv("AGRIINSIGHT_ARTIFACTS", str(artifact_root))
    dashboard_path = Path(__file__).parents[1] / "dashboard" / "app.py"
    return AppTest.from_file(str(dashboard_path), default_timeout=20).run()


def _dashboard_images(app: AppTest) -> Sequence[object]:
    """Use the public image element name while retaining the supported 1.58 API."""
    images = app.get("image")
    return images if images else app.get("imgs")


def _fake_report_bundle(
    gold,
    manifest,
    raw_request,
    **_kwargs,
) -> SimpleNamespace:
    request = CostReportRequest.from_mapping(raw_request, CostReportDomains.from_gold(gold))
    metadata = CostReportMetadata.from_manifest(manifest, request)
    csv = ReportArtifact("report.csv", "text/csv", b"csv")
    pdf = ReportArtifact("report.pdf", "application/pdf", b"pdf")
    return SimpleNamespace(
        request=request,
        metadata=metadata,
        csv=csv,
        pdf=pdf,
        xlsx=None,
        xlsx_unavailable_reason="XLSX adapter is not provisioned for this test.",
    )


def test_executive_dashboard_renders_pipeline_outputs(
    dashboard_artifact_root: Path, monkeypatch
) -> None:
    app = _run_dashboard(dashboard_artifact_root, monkeypatch)

    assert not app.exception
    assert app.title[0].value == "AgriInsight — Enterprise Agriculture Analytics"
    assert app.radio[0].options == [
        "Executive",
        "Farm Performance",
        "Inventory",
        "Crop Health",
        "Data Quality",
        "Cost Analysis",
    ]
    assert len(app.metric) == 8
    assert len(_dashboard_images(app)) == 1
    assert app.metric[0].label == "Doanh thu"
    assert app.metric[7].label == "Data validity"
    assert any("Insight" in heading.value for heading in app.subheader)

    app.radio[0].set_value("Farm Performance").run()
    assert not app.exception
    assert len(_dashboard_images(app)) == 1
    assert app.header[0].value == "Hiệu suất trang trại"
    assert app.metric[0].label == "Doanh thu"

    app.radio[0].set_value("Inventory").run()
    assert not app.exception
    assert len(_dashboard_images(app)) == 1
    assert app.header[0].value == "Quản lý và phân tích kho vật tư"
    assert app.metric[0].label == "Giá trị tồn kho"
    assert app.metric[5].label == "Days of supply TB"

    app.radio[0].set_value("Crop Health").run()
    assert not app.exception
    assert len(_dashboard_images(app)) == 1
    assert any("AI-generated demo evidence" in item.value for item in app.warning)
    assert app.header[0].value == "Sức khỏe cây trồng và môi trường"
    assert app.metric[0].label == "Khu vực theo dõi"
    assert app.metric[3].label == "Cảm biến offline"

    app.radio[0].set_value("Data Quality").run()
    assert not app.exception
    assert len(_dashboard_images(app)) == 1
    assert app.header[0].value == "Chất lượng và độ tin cậy dữ liệu"


def test_cost_analysis_builds_downloads_only_after_submit(
    dashboard_artifact_root: Path, monkeypatch
) -> None:
    calls: list[dict[str, object]] = []

    def fake_build(gold, manifest, raw_request, **kwargs):
        calls.append(dict(raw_request))
        return _fake_report_bundle(gold, manifest, raw_request, **kwargs)

    monkeypatch.setattr(
        "agriinsight.cost_report_service.build_cost_report_bundle", fake_build
    )
    app = _run_dashboard(dashboard_artifact_root, monkeypatch)
    app.radio("dashboard_page").set_value("Cost Analysis").run()

    assert not app.exception
    assert len(_dashboard_images(app)) == 1
    assert app.header[0].value == "Phân tích chi phí"
    assert app.get("download_button") == []
    farm_code = pd.read_csv(
        dashboard_artifact_root / "gold" / "cost_activity_detail.csv"
    ).iloc[0]["farm_code"]
    app.selectbox("cost_operating_farm").set_value(farm_code)
    app.button("cost_operating_submit").click().run()

    assert not app.exception
    assert calls == [{"scope": "operating", "farm": farm_code, "top_n": 15}]
    downloads = app.get("download_button")
    assert [element.proto.label for element in downloads] == [
        "Tải CSV",
        "Tải PDF",
        "XLSX không khả dụng",
    ]
    assert [element.proto.disabled for element in downloads] == [False, False, True]
    assert any("XLSX adapter" in caption.value for caption in app.caption)


def test_cost_analysis_surfaces_bounded_export_error(
    dashboard_artifact_root: Path, monkeypatch
) -> None:
    def reject_oversized(*_args, **_kwargs):
        raise ReportValidationError("The complete detail bundle is limited to 25,000 rows")

    monkeypatch.setattr(
        "agriinsight.cost_report_service.build_cost_report_bundle", reject_oversized
    )
    app = _run_dashboard(dashboard_artifact_root, monkeypatch)
    app.radio("dashboard_page").set_value("Cost Analysis").run()
    app.button("cost_operating_submit").click().run()

    assert not app.exception
    assert any("25,000" in error.value for error in app.error)
    assert app.get("download_button") == []


def test_cost_analysis_hides_runtime_paths_from_export_errors(
    dashboard_artifact_root: Path, monkeypatch
) -> None:
    leaked_path = r"C:\private\fonts\NotoSans-Regular.ttf"

    def reject_unavailable(*_args, **_kwargs):
        raise ExportUnavailable(f"Missing bundled font: {leaked_path}")

    monkeypatch.setattr(
        "agriinsight.cost_report_service.build_cost_report_bundle",
        reject_unavailable,
    )
    app = _run_dashboard(dashboard_artifact_root, monkeypatch)
    app.radio("dashboard_page").set_value("Cost Analysis").run()
    app.button("cost_operating_submit").click().run()

    assert not app.exception
    assert any("runtime xuất báo cáo chưa sẵn sàng" in error.value for error in app.error)
    assert all(leaked_path not in error.value for error in app.error)
    assert app.get("download_button") == []


def test_cost_analysis_missing_artifact_is_route_local(
    dashboard_artifact_root: Path, tmp_path: Path, monkeypatch
) -> None:
    copied_root = tmp_path / "artifacts"
    shutil.copytree(dashboard_artifact_root, copied_root)
    (copied_root / "gold" / "cost_summary.csv").unlink()

    app = _run_dashboard(copied_root, monkeypatch)
    assert not app.exception
    assert len(app.metric) == 8
    app.radio("dashboard_page").set_value("Cost Analysis").run()

    assert not app.exception
    assert any("Chưa đủ Cost Gold" in error.value for error in app.error)
    assert any("cost_summary.csv" in caption.value for caption in app.caption)
    assert all(str(copied_root) not in caption.value for caption in app.caption)


def test_cost_analysis_handles_checksum_mismatch_without_leaking_paths(
    dashboard_artifact_root: Path, tmp_path: Path, monkeypatch
) -> None:
    copied_root = tmp_path / "artifacts"
    shutil.copytree(dashboard_artifact_root, copied_root)
    summary_path = copied_root / "gold" / "cost_summary.csv"
    summary_path.write_bytes(summary_path.read_bytes() + b"\n")

    app = _run_dashboard(copied_root, monkeypatch)
    app.radio("dashboard_page").set_value("Cost Analysis").run()

    assert not app.exception
    assert any("snapshot nhất quán" in error.value for error in app.error)
    assert all(str(copied_root) not in error.value for error in app.error)


def test_cost_analysis_renders_procurement_drilldown_and_downloads(
    dashboard_artifact_root: Path, monkeypatch
) -> None:
    calls: list[dict[str, object]] = []

    def fake_build(gold, manifest, raw_request, **kwargs):
        calls.append(dict(raw_request))
        return _fake_report_bundle(gold, manifest, raw_request, **kwargs)

    monkeypatch.setattr(
        "agriinsight.cost_report_service.build_cost_report_bundle", fake_build
    )
    app = _run_dashboard(dashboard_artifact_root, monkeypatch)
    app.radio("dashboard_page").set_value("Cost Analysis").run()
    supplier_code = pd.read_csv(
        dashboard_artifact_root / "gold" / "procurement_detail.csv"
    ).iloc[0]["supplier_code"]
    app.selectbox("cost_procurement_supplier").set_value(supplier_code)
    app.button("cost_procurement_submit").click().run()

    assert not app.exception
    assert calls == [
        {"scope": "procurement", "supplier": supplier_code, "top_n": 15}
    ]
    assert any(
        heading.value == "Nhà cung cấp → kho → vật tư"
        for heading in app.subheader
    )
    assert [element.proto.label for element in app.get("download_button")] == [
        "Tải CSV",
        "Tải PDF",
        "XLSX không khả dụng",
    ]


def test_cost_analysis_handles_empty_operating_detail(
    dashboard_artifact_root: Path, tmp_path: Path, monkeypatch
) -> None:
    copied_root = tmp_path / "artifacts"
    shutil.copytree(dashboard_artifact_root, copied_root)
    detail_path = copied_root / "gold" / "cost_activity_detail.csv"
    pd.read_csv(detail_path).iloc[0:0].to_csv(detail_path, index=False)
    manifest_path = copied_root / "manifest.json"
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    manifest["checksums"]["gold/cost_activity_detail.csv"] = hashlib.sha256(
        detail_path.read_bytes()
    ).hexdigest()
    manifest_path.write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8"
    )

    app = _run_dashboard(copied_root, monkeypatch)
    app.radio("dashboard_page").set_value("Cost Analysis").run()

    assert not app.exception
    assert any("Không có hoạt động" in message.value for message in app.info)
    assert app.get("download_button") == []


def test_cost_analysis_drops_stale_bundle_when_cost_checksums_change(
    dashboard_artifact_root: Path, tmp_path: Path, monkeypatch
) -> None:
    copied_root = tmp_path / "artifacts"
    shutil.copytree(dashboard_artifact_root, copied_root)
    monkeypatch.setattr(
        "agriinsight.cost_report_service.build_cost_report_bundle",
        _fake_report_bundle,
    )
    app = _run_dashboard(copied_root, monkeypatch)
    app.radio("dashboard_page").set_value("Cost Analysis").run()
    app.button("cost_operating_submit").click().run()
    assert len(app.get("download_button")) == 3

    cost_summary_path = copied_root / "gold" / "cost_summary.csv"
    summary = pd.read_csv(cost_summary_path)
    summary.loc[0, "operating_total_cost_vnd"] += 1
    summary.to_csv(cost_summary_path, index=False)

    manifest_path = copied_root / "manifest.json"
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    manifest["configuration"]["farm_count"] += 1
    manifest["checksums"]["gold/cost_summary.csv"] = hashlib.sha256(
        cost_summary_path.read_bytes()
    ).hexdigest()
    manifest_path.write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    app.run()

    assert not app.exception
    assert app.get("download_button") == []
