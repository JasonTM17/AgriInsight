from __future__ import annotations

import pytest

from agriinsight.analytics_api import __main__ as entrypoint


def test_internal_entrypoint_uses_safe_defaults(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    observed: dict[str, object] = {}

    def run(app: str, **options: object) -> None:
        observed["app"] = app
        observed.update(options)

    monkeypatch.delenv("AGRIINSIGHT_ANALYTICS_BIND_HOST", raising=False)
    monkeypatch.delenv("AGRIINSIGHT_ANALYTICS_PORT", raising=False)
    monkeypatch.setattr(entrypoint.uvicorn, "run", run)

    entrypoint.main()

    assert observed == {
        "app": "agriinsight.analytics_api.app:create_app",
        "factory": True,
        "host": "127.0.0.1",
        "port": 8081,
        "server_header": False,
    }


@pytest.mark.parametrize(
    ("key", "value", "message"),
    [
        ("AGRIINSIGHT_ANALYTICS_BIND_HOST", "public.example", "loopback"),
        ("AGRIINSIGHT_ANALYTICS_PORT", "70000", "valid range"),
    ],
)
def test_internal_entrypoint_rejects_unsafe_network_configuration(
    monkeypatch: pytest.MonkeyPatch,
    key: str,
    value: str,
    message: str,
) -> None:
    monkeypatch.setenv(key, value)

    with pytest.raises(ValueError, match=message):
        entrypoint.main()
