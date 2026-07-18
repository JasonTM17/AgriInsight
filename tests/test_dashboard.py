from __future__ import annotations

from datetime import date
from pathlib import Path

from streamlit.testing.v1 import AppTest

from agriinsight.config import GenerationConfig
from agriinsight.pipeline import run_pipeline


def test_executive_dashboard_renders_pipeline_outputs(
    tmp_path: Path, monkeypatch
) -> None:
    artifact_root = tmp_path / "artifacts"
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
    monkeypatch.setenv("AGRIINSIGHT_ARTIFACTS", str(artifact_root))

    dashboard_path = Path(__file__).parents[1] / "dashboard" / "app.py"
    app = AppTest.from_file(str(dashboard_path), default_timeout=15).run()

    assert not app.exception
    assert app.title[0].value == "AgriInsight — Enterprise Agriculture Analytics"
    assert len(app.metric) == 8
    assert app.metric[0].label == "Doanh thu"
    assert app.metric[7].label == "Data validity"
    assert any("Insight" in heading.value for heading in app.subheader)

    app.radio[0].set_value("Inventory").run()
    assert not app.exception
    assert app.header[0].value == "Quản lý và phân tích kho vật tư"
    assert app.metric[0].label == "Giá trị tồn kho"
    assert app.metric[5].label == "Days of supply TB"

    app.radio[0].set_value("Crop Health").run()
    assert not app.exception
    assert app.header[0].value == "Sức khỏe cây trồng và môi trường"
    assert app.metric[0].label == "Khu vực theo dõi"
    assert app.metric[3].label == "Cảm biến offline"

    app.radio[0].set_value("Data Quality").run()
    assert not app.exception
    assert app.header[0].value == "Chất lượng và độ tin cậy dữ liệu"
