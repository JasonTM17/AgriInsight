from __future__ import annotations

import asyncio
import json
from pathlib import Path

import httpx
import pytest

from agriinsight.analytics_api.assistant_provider_evaluation_workload import (
    _concepts_match,
    run_provider_evaluation,
)
from agriinsight.analytics_api.assistant_settings import AssistantSettings


def _settings() -> AssistantSettings:
    return AssistantSettings.from_environment(
        {
            "AGRIINSIGHT_ASSISTANT_ENABLED": "true",
            "AGRIINSIGHT_LLM_PROVIDER": "deepseek",
            "AGRIINSIGHT_LLM_BASE_URL": "https://api.deepseek.com",
            "AGRIINSIGHT_LLM_MODEL": "deepseek-v4-flash",
            "AGRIINSIGHT_LLM_API_KEY": "x" * 32,
            "AGRIINSIGHT_LLM_THINKING_ENABLED": "false",
        }
    )


def test_closed_fixture_workload_uses_provider_only_for_answerable_cases() -> None:
    calls = 0

    def handler(request: httpx.Request) -> httpx.Response:
        nonlocal calls
        calls += 1
        body = json.loads(request.content)
        user_payload = json.loads(body["messages"][1]["content"])
        question = user_payload["question"].casefold()
        source_type = (
            "farm-performance"
            if "profit" in question or "lợi nhuận" in question or "yield" in question or "năng suất" in question
            else "cost"
            if "cost" in question or "chi phí" in question or "budget" in question or "ngân sách" in question
            else "inventory"
            if "stock" in question or "inventory" in question or "kho" in question
            else "crop-health"
            if "crop health" in question or "sâu bệnh" in question
            else "data-quality"
        )
        evidence = next(
            item for item in user_payload["untrusted_evidence"]
            if item["source_type"] == source_type
        )
        evidence_id = evidence["evidence_id"]
        answer = evidence["content"].rstrip(".").replace("6.4", "6,4")
        content = json.dumps(
            {
                "status": "answered",
                "answer": f"{answer} [{evidence_id}]",
                "citation_ids": [evidence_id],
            },
            ensure_ascii=False,
            separators=(",", ":"),
        )
        response = {
            "choices": [
                {
                    "finish_reason": "stop",
                    "message": {"role": "assistant", "content": content},
                }
            ],
            "usage": {
                "prompt_tokens": 100,
                "completion_tokens": 20,
                "total_tokens": 120,
                "prompt_cache_hit_tokens": 10,
                "prompt_cache_miss_tokens": 90,
            },
        }
        return httpx.Response(200, json=response, request=request)

    async def exercise():
        async with httpx.AsyncClient(
            base_url="https://api.deepseek.com",
            transport=httpx.MockTransport(handler),
        ) as client:
            return await run_provider_evaluation(
                _settings(),
                source_sha="a" * 40,
                http_client=client,
            )

    payload = asyncio.run(exercise()).to_dict()

    assert calls == 20
    assert payload["sample_count"] == 30
    assert payload["provider_expected_count"] == 20
    assert payload["provider_call_count"] == 20
    assert payload["refusal_expected_count"] == 10
    assert payload["outcome_counts"] == {
        "answered": 20,
        "insufficient_evidence": 10,
    }
    assert payload["gates"]["passed"] is True
    serialized = json.dumps(payload, ensure_ascii=False)
    for sensitive in (
        "FARM-01",
        "provider-evaluation-",
        "20000000-0000-4000-8000-000000000001",
    ):
        assert sensitive not in serialized


def test_request_preflight_rejects_before_any_provider_dispatch(tmp_path: Path) -> None:
    fixture = json.loads(
        (Path(__file__).parents[1] / "fixtures" / "assistant-retrieval-evaluation-v1.json").read_text(
            encoding="utf-8"
        )
    )
    for index in range(3):
        fixture["corpus"][index]["title"] = f"oversize evidence {index}"
        fixture["corpus"][index]["content"] = "oversize " + ("x" * 3_990)
    fixture["cases"][0]["question"] = "oversize"
    fixture_path = tmp_path / "oversized-fixture.json"
    fixture_path.write_text(json.dumps(fixture), encoding="utf-8")
    calls = 0

    def handler(request: httpx.Request) -> httpx.Response:
        nonlocal calls
        calls += 1
        return httpx.Response(500, request=request)

    async def exercise() -> None:
        async with httpx.AsyncClient(
            base_url="https://api.deepseek.com",
            transport=httpx.MockTransport(handler),
        ) as client:
            await run_provider_evaluation(
                _settings(),
                source_sha="a" * 40,
                fixture_path=fixture_path,
                http_client=client,
            )

    try:
        asyncio.run(exercise())
    except RuntimeError as error:
        assert str(error) == "assistant provider request preflight exceeded byte ceiling"
    else:
        raise AssertionError("oversized request preflight did not fail closed")
    assert calls == 0


def test_concept_matching_requires_complete_normalized_tokens() -> None:
    assert not _concepts_match("days of supply is 14", [["4"]])
    assert not _concepts_match("quarantine rows: 120", [["12"]])
    assert _concepts_match("Năng suất là 6,4 tấn.", [["6.4", "6,4"]])


def test_over_limit_usage_stops_before_any_later_batch_dispatches() -> None:
    calls = 0

    def handler(request: httpx.Request) -> httpx.Response:
        nonlocal calls
        calls += 1
        body = json.loads(request.content)
        user_payload = json.loads(body["messages"][1]["content"])
        evidence_id = user_payload["untrusted_evidence"][0]["evidence_id"]
        response = {
            "choices": [
                {
                    "finish_reason": "stop",
                    "message": {
                        "role": "assistant",
                        "content": json.dumps(
                            {
                                "status": "answered",
                                "answer": f"Verified [{evidence_id}]",
                                "citation_ids": [evidence_id],
                            },
                            separators=(",", ":"),
                        ),
                    },
                }
            ],
            "usage": {
                "prompt_tokens": 100,
                "completion_tokens": 513,
                "total_tokens": 613,
                "prompt_cache_hit_tokens": 0,
                "prompt_cache_miss_tokens": 100,
            },
        }
        return httpx.Response(200, json=response, request=request)

    async def exercise() -> None:
        async with httpx.AsyncClient(
            base_url="https://api.deepseek.com",
            transport=httpx.MockTransport(handler),
        ) as client:
            await run_provider_evaluation(
                _settings(),
                source_sha="a" * 40,
                http_client=client,
            )

    with pytest.raises(
        RuntimeError,
        match="^assistant provider response exceeded evaluation usage limits$",
    ):
        asyncio.run(exercise())
    assert 1 <= calls <= 3
