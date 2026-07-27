from __future__ import annotations

import asyncio
import logging
from datetime import date
from uuid import UUID

import pytest

from agriinsight.analytics_api.assistant_models import (
    AssistantAnswer,
    AssistantQuery,
    AssistantUsage,
    EvidenceCitation,
)
from agriinsight.analytics_api.assistant_observability import AssistantTelemetry
from agriinsight.analytics_api.assistant_retrieval import (
    EvidenceChunk,
    EvidenceRetriever,
)
from agriinsight.analytics_api.assistant_service import AssistantService
from agriinsight.analytics_api.auth_scope import AuthorizedScope


TENANT_ID = UUID("20000000-0000-4000-8000-000000000001")


class AnsweringGenerator:
    async def generate(self, _query, evidence, _tenant_id):
        identifier = evidence[0].chunk.evidence_id
        return AssistantAnswer(
            status="answered",
            answer=f"Lợi nhuận đã xác minh [{identifier}].",
            citations=[
                EvidenceCitation(
                    evidenceId=identifier,
                    title=evidence[0].chunk.title,
                    excerpt=evidence[0].chunk.content,
                    sourceType=evidence[0].chunk.source_type,
                    asOf=evidence[0].chunk.as_of,
                )
            ],
            usage=AssistantUsage(
                promptTokens=80,
                completionTokens=20,
                totalTokens=100,
                promptCacheHitTokens=30,
                promptCacheMissTokens=50,
            ),
        )


def test_telemetry_contains_only_allowlisted_operational_fields(
    caplog: pytest.LogCaptureFixture,
) -> None:
    logger = logging.getLogger("assistant-observability-test")
    service = AssistantService(
        EvidenceRetriever(max_items=5, max_characters=12_000),
        AnsweringGenerator(),
        AssistantTelemetry(logger),
    )
    question = "Lợi nhuận FARM-01 với nội dung nhạy cảm?"
    evidence_content = "FARM-01 có lợi nhuận 200 triệu đồng."

    with caplog.at_level(logging.INFO, logger=logger.name):
        asyncio.run(
            service.answer(
                AssistantQuery(question=question),
                _scope(),
                [_chunk(evidence_content)],
                "correlation-safe",
            )
        )

    record = caplog.records[-1]
    event = record.assistant_event
    assert event == {
        "completion_tokens": 20,
        "correlation_id": "correlation-safe",
        "latency_ms": event["latency_ms"],
        "outcome": "answered",
        "prompt_cache_hit_tokens": 30,
        "prompt_cache_miss_tokens": 50,
        "prompt_tokens": 80,
        "provider_code": "deepseek-v4-flash",
        "refusal_reason": None,
        "retrieval_count": 1,
        "total_tokens": 100,
    }
    assert question not in caplog.text
    assert evidence_content not in caplog.text
    assert "Lợi nhuận đã xác minh" not in caplog.text


def _scope() -> AuthorizedScope:
    return AuthorizedScope(
        farm_codes=frozenset({"FARM-01"}),
        farm_tenant_wide=False,
        tenant_id=TENANT_ID,
        warehouse_codes=frozenset(),
        warehouse_tenant_wide=False,
    )


def _chunk(content: str) -> EvidenceChunk:
    return EvidenceChunk(
        evidence_id="ev-farm",
        title="FARM-01",
        content=content,
        source_type="farm-performance",
        as_of=date(2026, 7, 27),
        tenant_id=TENANT_ID,
        farm_codes=frozenset({"FARM-01"}),
        warehouse_codes=frozenset(),
    )
