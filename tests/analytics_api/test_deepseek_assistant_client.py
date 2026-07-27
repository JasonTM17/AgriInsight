from __future__ import annotations

import asyncio
import json
from datetime import date
from uuid import UUID

import httpx
import pytest

from agriinsight.analytics_api.assistant_models import AssistantQuery
from agriinsight.analytics_api.assistant_retrieval import (
    EvidenceChunk,
    RetrievedEvidence,
)
from agriinsight.analytics_api.assistant_settings import AssistantSettings
from agriinsight.analytics_api.deepseek_assistant_client import (
    AssistantProviderError,
    DeepSeekAssistantClient,
)


TENANT_ID = UUID("20000000-0000-4000-8000-000000000001")


def _settings() -> AssistantSettings:
    return AssistantSettings(
        enabled=True,
        api_key="test-only-key-material-000000",
    ).validated()


def _evidence() -> list[RetrievedEvidence]:
    return [
        RetrievedEvidence(
            chunk=EvidenceChunk(
                evidence_id="ev-farm-01",
                title="Hiệu quả FARM-01",
                content="Lợi nhuận FARM-01 là 240 triệu đồng.",
                source_type="farm-performance",
                as_of=date(2026, 7, 27),
                tenant_id=TENANT_ID,
                farm_codes=frozenset({"FARM-01"}),
                warehouse_codes=frozenset(),
            ),
            score=8_000,
        )
    ]


def test_valid_answer_uses_fixed_model_bounded_prompt_and_citations() -> None:
    observed: dict[str, object] = {}

    def handler(request: httpx.Request) -> httpx.Response:
        observed["authorization"] = request.headers["Authorization"]
        payload = json.loads(request.content)
        observed["payload"] = payload
        assert _settings().api_key not in request.content.decode()
        assert str(TENANT_ID) not in request.content.decode()
        return httpx.Response(
            200,
            json={
                "choices": [
                    {
                        "message": {
                            "content": json.dumps(
                                {
                                    "status": "answered",
                                    "answer": (
                                        "Lợi nhuận là 240 triệu đồng "
                                        "[ev-farm-01]."
                                    ),
                                    "citation_ids": ["ev-farm-01"],
                                }
                            )
                        }
                    }
                ],
                "usage": {
                    "prompt_tokens": 300,
                    "completion_tokens": 30,
                    "total_tokens": 330,
                    "prompt_cache_hit_tokens": 100,
                    "prompt_cache_miss_tokens": 200,
                    "prompt_tokens_details": {"cached_tokens": 100},
                },
            },
        )

    async def exercise():
        async with httpx.AsyncClient(
            base_url="https://api.deepseek.com",
            transport=httpx.MockTransport(handler),
        ) as http_client:
            return await DeepSeekAssistantClient(
                _settings(), http_client
            ).generate(
                AssistantQuery(question="Lợi nhuận FARM-01?"),
                _evidence(),
                TENANT_ID,
            )

    answer = asyncio.run(exercise())

    assert answer.status == "answered"
    assert answer.citations[0].evidence_id == "ev-farm-01"
    assert observed["authorization"] == (
        "Bearer test-only-key-material-000000"
    )
    request_payload = observed["payload"]
    assert isinstance(request_payload, dict)
    assert request_payload["model"] == "deepseek-v4-flash"
    assert request_payload["thinking"] == {"type": "disabled"}
    assert request_payload["stream"] is False
    assert request_payload["max_tokens"] == 1_200


@pytest.mark.parametrize(
    ("response", "expected_code", "retryable"),
    [
        (httpx.Response(401), "assistant_configuration_error", False),
        (httpx.Response(429), "assistant_provider_busy", True),
        (httpx.Response(503), "assistant_provider_unavailable", True),
        (
            httpx.Response(
                200,
                json={
                    "choices": [
                        {
                            "message": {
                                "content": json.dumps(
                                    {
                                        "status": "answered",
                                        "answer": "Không hợp lệ [ev-unknown].",
                                        "citation_ids": ["ev-unknown"],
                                    }
                                )
                            }
                        }
                    ],
                    "usage": {
                        "prompt_tokens": 1,
                        "completion_tokens": 1,
                        "total_tokens": 2,
                        "prompt_cache_hit_tokens": 0,
                        "prompt_cache_miss_tokens": 1,
                    },
                },
            ),
            "assistant_provider_invalid_response",
            False,
        ),
    ],
)
def test_provider_failures_are_typed_and_redacted(
    response: httpx.Response,
    expected_code: str,
    retryable: bool,
) -> None:
    async def exercise() -> None:
        async with httpx.AsyncClient(
            base_url="https://api.deepseek.com",
            transport=httpx.MockTransport(lambda _request: response),
        ) as http_client:
            await DeepSeekAssistantClient(_settings(), http_client).generate(
                AssistantQuery(question="Lợi nhuận FARM-01?"),
                _evidence(),
                TENANT_ID,
            )

    with pytest.raises(AssistantProviderError) as captured:
        asyncio.run(exercise())

    assert captured.value.code == expected_code
    assert captured.value.retryable is retryable
    assert "ev-unknown" not in str(captured.value)


def test_timeout_is_retryable_and_does_not_leak_transport_detail() -> None:
    def handler(request: httpx.Request) -> httpx.Response:
        raise httpx.ReadTimeout("secret upstream diagnostic", request=request)

    async def exercise() -> None:
        async with httpx.AsyncClient(
            base_url="https://api.deepseek.com",
            transport=httpx.MockTransport(handler),
        ) as http_client:
            await DeepSeekAssistantClient(_settings(), http_client).generate(
                AssistantQuery(question="Lợi nhuận FARM-01?"),
                _evidence(),
                TENANT_ID,
            )

    with pytest.raises(AssistantProviderError) as captured:
        asyncio.run(exercise())

    assert captured.value.code == "assistant_provider_timeout"
    assert captured.value.retryable is True
    assert "secret upstream diagnostic" not in str(captured.value)


def test_oversized_provider_response_is_rejected_before_json_parsing() -> None:
    async def exercise() -> None:
        async with httpx.AsyncClient(
            base_url="https://api.deepseek.com",
            transport=httpx.MockTransport(
                lambda _request: httpx.Response(200, content=b"x" * 131_073)
            ),
        ) as http_client:
            await DeepSeekAssistantClient(_settings(), http_client).generate(
                AssistantQuery(question="Lợi nhuận FARM-01?"),
                _evidence(),
                TENANT_ID,
            )

    with pytest.raises(AssistantProviderError) as captured:
        asyncio.run(exercise())

    assert captured.value.code == "assistant_provider_invalid_response"
    assert captured.value.retryable is False
