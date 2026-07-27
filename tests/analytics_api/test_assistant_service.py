from __future__ import annotations

import asyncio
from datetime import date
from uuid import UUID

from agriinsight.analytics_api.assistant_models import (
    AssistantAnswer,
    AssistantQuery,
    AssistantUsage,
)
from agriinsight.analytics_api.assistant_retrieval import (
    EvidenceChunk,
    EvidenceRetriever,
)
from agriinsight.analytics_api.assistant_service import AssistantService
from agriinsight.analytics_api.auth_scope import AuthorizedScope


TENANT_ID = UUID("20000000-0000-4000-8000-000000000001")


class RecordingGenerator:
    def __init__(self) -> None:
        self.calls = 0

    async def generate(self, query, evidence, tenant_id):
        self.calls += 1
        assert query.question
        assert evidence
        assert tenant_id == TENANT_ID
        return AssistantAnswer(
            status="insufficient_evidence",
            answer="Không đủ bằng chứng đã xác minh.",
            citations=[],
            usage=AssistantUsage(
                promptTokens=0,
                completionTokens=0,
                totalTokens=0,
                promptCacheHitTokens=0,
                promptCacheMissTokens=0,
            ),
        )


def _scope() -> AuthorizedScope:
    return AuthorizedScope(
        farm_codes=frozenset({"FARM-01"}),
        farm_tenant_wide=False,
        tenant_id=TENANT_ID,
        warehouse_codes=frozenset(),
        warehouse_tenant_wide=False,
    )


def _chunk() -> EvidenceChunk:
    return EvidenceChunk(
        evidence_id="ev-farm",
        title="FARM-01",
        content="FARM-01 có lợi nhuận 200 triệu đồng.",
        source_type="farm-performance",
        as_of=date(2026, 7, 27),
        tenant_id=TENANT_ID,
        farm_codes=frozenset({"FARM-01"}),
        warehouse_codes=frozenset(),
    )


def test_no_evidence_returns_local_refusal_without_provider_call() -> None:
    generator = RecordingGenerator()
    service = AssistantService(
        EvidenceRetriever(max_items=8, max_characters=12_000),
        generator,
    )

    result = asyncio.run(
        service.answer(
            AssistantQuery(question="Ai vô địch bóng đá?"),
            _scope(),
            [_chunk()],
        )
    )

    assert result.status == "insufficient_evidence"
    assert result.citations == []
    assert result.usage.total_tokens == 0
    assert generator.calls == 0


def test_relevant_evidence_calls_provider_once() -> None:
    generator = RecordingGenerator()
    service = AssistantService(
        EvidenceRetriever(max_items=8, max_characters=12_000),
        generator,
    )

    asyncio.run(
        service.answer(
            AssistantQuery(question="Lợi nhuận FARM-01?"),
            _scope(),
            [_chunk()],
        )
    )

    assert generator.calls == 1
