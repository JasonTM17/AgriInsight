from pathlib import Path


def test_compose_publishes_dashboard_on_loopback_only() -> None:
    compose = (Path(__file__).parents[1] / "compose.yaml").read_text(encoding="utf-8")

    assert '      - "127.0.0.1:8501:8501"' in compose
    assert '      - "8501:8501"' not in compose


def test_compose_keeps_gold_read_only_with_writable_report_temp() -> None:
    compose = (Path(__file__).parents[1] / "compose.yaml").read_text(encoding="utf-8")

    assert "      - ./artifacts:/app/artifacts:ro" in compose
    assert "      - ./artifacts/_tmp:/app/artifacts/_tmp" in compose
