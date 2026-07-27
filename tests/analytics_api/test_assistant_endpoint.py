from __future__ import annotations

from agriinsight.analytics_api.assistant_models import (
    AssistantAnswer,
    AssistantUsage,
)
from agriinsight.analytics_api.assistant_settings import AssistantSettings
from agriinsight.analytics_api.deepseek_assistant_client import (
    AssistantProviderError,
)


class RecordingAssistantService:
    def __init__(self) -> None:
        self.calls = []

    async def answer(self, query, scope, corpus, correlation_id):
        self.calls.append((query, scope, corpus, correlation_id))
        return AssistantAnswer(
            status="insufficient_evidence",
            answer="Không đủ bằng chứng AgriInsight đã xác minh.",
            citations=[],
            usage=AssistantUsage(
                promptTokens=0,
                completionTokens=0,
                totalTokens=0,
                promptCacheHitTokens=0,
                promptCacheMissTokens=0,
            ),
        )


class FailingAssistantService:
    async def answer(self, _query, _scope, _corpus, _correlation_id):
        raise AssistantProviderError(
            "assistant_provider_busy",
            "internal provider detail",
            retryable=True,
        )


def _enabled() -> AssistantSettings:
    return AssistantSettings(
        enabled=True,
        api_key="test-only-key-material-000000",
    ).validated()


def test_disabled_assistant_has_no_route(api_factory) -> None:
    _app, client, _spring = api_factory()

    response = client.post(
        "/internal/v1/assistant/query",
        headers={"Authorization": "Bearer test-token"},
        json={"question": "Tóm tắt dữ liệu."},
    )

    assert response.status_code == 404
    assert response.json()["error"]["code"] == "route_not_found"


def test_authorized_query_uses_server_scope_and_verified_corpus(api_factory) -> None:
    service = RecordingAssistantService()
    _app, client, spring = api_factory(
        assistant_settings=_enabled(),
        assistant_service=service,
    )

    response = client.post(
        "/internal/v1/assistant/query",
        headers={"Authorization": "Bearer test-token"},
        json={"question": "Trang trại nào có lợi nhuận cao?"},
    )

    assert response.status_code == 200
    assert response.json()["status"] == "insufficient_evidence"
    assert len(service.calls) == 1
    query, scope, corpus, correlation_id = service.calls[0]
    assert query.question == "Trang trại nào có lợi nhuận cao?"
    assert scope.tenant_id == spring.user.tenantId
    assert scope.farm_tenant_wide is True
    assert scope.warehouse_tenant_wide is True
    assert corpus
    assert correlation_id
    assert all(chunk.tenant_id == spring.user.tenantId for chunk in corpus)


def test_supplier_is_denied_before_corpus_or_provider(api_factory) -> None:
    service = RecordingAssistantService()
    _app, client, _spring = api_factory(
        roles={"SUPPLIER"},
        assistant_settings=_enabled(),
        assistant_service=service,
    )

    response = client.post(
        "/internal/v1/assistant/query",
        headers={"Authorization": "Bearer test-token"},
        json={"question": "Hiển thị dữ liệu bí mật."},
    )

    assert response.status_code == 403
    assert response.json()["error"]["code"] == "analytics_forbidden"
    assert service.calls == []


def test_client_cannot_supply_tenant_or_model(api_factory) -> None:
    service = RecordingAssistantService()
    _app, client, _spring = api_factory(
        assistant_settings=_enabled(),
        assistant_service=service,
    )

    response = client.post(
        "/internal/v1/assistant/query",
        headers={"Authorization": "Bearer test-token"},
        json={
            "question": "Tóm tắt dữ liệu.",
            "tenantId": "attacker",
            "model": "arbitrary",
        },
    )

    assert response.status_code == 422
    assert service.calls == []


def test_provider_error_is_sanitized(api_factory) -> None:
    _app, client, _spring = api_factory(
        assistant_settings=_enabled(),
        assistant_service=FailingAssistantService(),
    )

    response = client.post(
        "/internal/v1/assistant/query",
        headers={"Authorization": "Bearer test-token"},
        json={"question": "Tóm tắt dữ liệu."},
    )

    assert response.status_code == 503
    payload = response.json()
    assert payload["error"]["code"] == "assistant_provider_busy"
    assert "internal provider detail" not in payload["error"]["message"]
