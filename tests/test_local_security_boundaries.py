from pathlib import Path


def test_compose_publishes_dashboard_on_loopback_only() -> None:
    compose = (Path(__file__).parents[1] / "compose.yaml").read_text(encoding="utf-8")

    assert '      - "127.0.0.1:8501:8501"' in compose
    assert '      - "8501:8501"' not in compose


def test_compose_keeps_gold_read_only_with_writable_report_temp() -> None:
    compose = (Path(__file__).parents[1] / "compose.yaml").read_text(encoding="utf-8")

    assert "      - ./artifacts:/app/artifacts:ro" in compose
    assert "      - ./artifacts/_tmp:/app/artifacts/_tmp" in compose


def test_python_image_is_pinned_non_root_and_has_allowlisted_context() -> None:
    root = Path(__file__).parents[1]
    dockerfile = (root / "Dockerfile").read_text(encoding="utf-8")
    dockerignore = (root / ".dockerignore").read_text(encoding="utf-8")

    assert "FROM python:3.13-slim@sha256:" in dockerfile
    assert "USER agriinsight" in dockerfile
    assert dockerignore.startswith("**\n")
    assert "!src/**" in dockerignore
    assert "!.env" not in dockerignore
    assert "!artifacts" not in dockerignore
