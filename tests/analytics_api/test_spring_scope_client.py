from __future__ import annotations

import asyncio
import json
from pathlib import Path
from uuid import UUID

import httpx
import pytest

from agriinsight.analytics_api.errors import ApiProblem
from agriinsight.analytics_api.settings import (
    AnalyticsSettings,
    AnalyticsSettingsError,
)
from agriinsight.analytics_api.spring_scope_client import SpringScopeClient


def _settings(**overrides) -> AnalyticsSettings:
    values = {
        "artifact_root": Path("."),
        "demo_tenant_id": UUID("20000000-0000-4000-8000-000000000001"),
        "reconciliation_report": Path("reconciliation.json"),
        "spring_base_url": "http://spring.test",
    }
    values.update(overrides)
    return AnalyticsSettings(**values)


def _user_payload() -> dict[str, object]:
    return {
        "assurance": "oidc",
        "displayName": "Test User",
        "email": None,
        "permissions": ["FARM_READ"],
        "profileId": "20000000-0000-4000-8000-000000000011",
        "roles": ["FARM_MANAGER"],
        "tenantCode": "AGRIINSIGHT_DEMO",
        "tenantId": "20000000-0000-4000-8000-000000000001",
    }


def test_programmatic_settings_use_the_same_fixed_origin_guard() -> None:
    with pytest.raises(AnalyticsSettingsError, match="fixed HTTP origin"):
        _settings(spring_base_url="https://user:password@spring.test/api").validated()

    assert (
        _settings(spring_base_url="http://spring.test/").validated().spring_base_url
        == "http://spring.test"
    )


def test_upstream_propagates_correlation_and_bearer() -> None:
    observed: dict[str, str] = {}

    def handler(request: httpx.Request) -> httpx.Response:
        observed["authorization"] = request.headers["Authorization"]
        observed["correlation"] = request.headers["X-Correlation-Id"]
        return httpx.Response(200, json=_user_payload())

    async def exercise() -> None:
        async with httpx.AsyncClient(
            base_url="http://spring.test",
            transport=httpx.MockTransport(handler),
        ) as client:
            result = await SpringScopeClient(
                _settings(), client
            ).current_user("Bearer opaque", "correlation-001")
            assert result.roles == ["FARM_MANAGER"]

    asyncio.run(exercise())
    assert observed == {
        "authorization": "Bearer opaque",
        "correlation": "correlation-001",
    }


def test_safe_get_retry_budget_is_bounded() -> None:
    calls = 0

    def handler(_request: httpx.Request) -> httpx.Response:
        nonlocal calls
        calls += 1
        if calls < 3:
            return httpx.Response(503, json={"detail": "temporary"})
        return httpx.Response(200, json=_user_payload())

    async def exercise() -> None:
        async with httpx.AsyncClient(
            base_url="http://spring.test",
            transport=httpx.MockTransport(handler),
        ) as client:
            result = await SpringScopeClient(
                _settings(upstream_attempts=3), client
            ).current_user("Bearer opaque", "correlation-002")
            assert result.tenantCode == "AGRIINSIGHT_DEMO"

    asyncio.run(exercise())
    assert calls == 3


@pytest.mark.parametrize(
    "response",
    [
        httpx.Response(302, headers={"Location": "http://evil.invalid"}),
        httpx.Response(200, headers={"Content-Length": "not-a-number"}),
        httpx.Response(200, content=b"{" + b"x" * 300_000 + b"}"),
    ],
)
def test_redirect_and_unusable_payloads_fail_closed(
    response: httpx.Response,
) -> None:
    def handler(_request: httpx.Request) -> httpx.Response:
        return response

    async def exercise() -> None:
        async with httpx.AsyncClient(
            base_url="http://spring.test",
            transport=httpx.MockTransport(handler),
        ) as client:
            await SpringScopeClient(
                _settings(upstream_attempts=1), client
            ).current_user("Bearer opaque", "correlation-003")

    with pytest.raises(ApiProblem) as captured:
        asyncio.run(exercise())
    assert captured.value.code == "spring_upstream_failure"


def test_strict_upstream_shape_rejects_contract_drift() -> None:
    payload = _user_payload()
    payload["unexpected"] = "drift"

    async def exercise() -> None:
        async with httpx.AsyncClient(
            base_url="http://spring.test",
            transport=httpx.MockTransport(
                lambda _request: httpx.Response(
                    200,
                    content=json.dumps(payload).encode(),
                )
            ),
        ) as client:
            await SpringScopeClient(
                _settings(), client
            ).current_user("Bearer opaque", "correlation-004")

    with pytest.raises(ApiProblem) as captured:
        asyncio.run(exercise())
    assert captured.value.status_code == 502
    assert "unexpected" not in captured.value.safe_message
