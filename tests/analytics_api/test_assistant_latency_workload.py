from __future__ import annotations

import asyncio
from datetime import date
from uuid import UUID

from agriinsight.analytics_api.assistant_latency_evaluation import (
    summarize_assistant_telemetry,
)
from agriinsight.analytics_api.assistant_models import (
    AssistantAnswer,
    AssistantQuery,
    AssistantUsage,
    EvidenceCitation,
)
from agriinsight.analytics_api.assistant_observability import (
    AssistantTelemetryEvent,
)
from agriinsight.analytics_api.assistant_retrieval import (
    EvidenceChunk,
    EvidenceRetriever,
    RetrievedEvidence,
)
from agriinsight.analytics_api.assistant_service import AssistantService
from agriinsight.analytics_api.auth_scope import AuthorizedScope


TENANT_ID = UUID("20000000-0000-4000-8000-000000000001")


class CapturingTelemetry:
    def __init__(self) -> None:
        self.events: list[AssistantTelemetryEvent] = []

    def record(self, event: AssistantTelemetryEvent) -> None:
        self.events.append(event)


class MockProviderError(RuntimeError):
    code = "mock_provider_failure"


class DelayedGenerator:
    def __init__(self) -> None:
        self.calls = 0

    async def generate(
        self,
        query: AssistantQuery,
        evidence: list[RetrievedEvidence],
        tenant_id: UUID,
    ) -> AssistantAnswer:
        self.calls += 1
        assert tenant_id == TENANT_ID
        assert evidence
        await asyncio.sleep(0.001)
        if "provider error" in query.question:
            raise MockProviderError("mock provider diagnostic must not aggregate")

        chunk = evidence[0].chunk
        return AssistantAnswer(
            status="answered",
            answer=f"Kết quả đã xác minh [{chunk.evidence_id}].",
            citations=[
                EvidenceCitation(
                    evidenceId=chunk.evidence_id,
                    title=chunk.title,
                    excerpt=chunk.content,
                    sourceType=chunk.source_type,
                    asOf=chunk.as_of,
                )
            ],
            usage=_usage(),
        )


def test_concurrent_service_telemetry_has_exact_redacted_outcomes() -> None:
    async def exercise() -> tuple[list[object], CapturingTelemetry, DelayedGenerator]:
        telemetry = CapturingTelemetry()
        generator = DelayedGenerator()
        service = AssistantService(
            EvidenceRetriever(max_items=5, max_characters=12_000),
            generator,
            telemetry=telemetry,
        )
        corpus = [_chunk()]
        questions = (
            "lợi nhuận answer one",
            "lợi nhuận answer two",
            "zzzxxyy no-evidence one",
            "zzzxxyy no-evidence two",
            "lợi nhuận provider error one",
            "lợi nhuận provider error two",
        )
        requests = [
            service.answer(
                AssistantQuery(question=question),
                _scope(),
                corpus,
                correlation_id=f"correlation-id-{index}",
            )
            for index, question in enumerate(questions, start=1)
        ]
        return (
            list(await asyncio.gather(*requests, return_exceptions=True)),
            telemetry,
            generator,
        )

    results, telemetry, generator = asyncio.run(exercise())

    assert len(results) == 6
    assert sum(isinstance(result, MockProviderError) for result in results) == 2
    assert sum(
        isinstance(result, AssistantAnswer) and result.status == "answered"
        for result in results
    ) == 2
    assert sum(
        isinstance(result, AssistantAnswer)
        and result.status == "insufficient_evidence"
        for result in results
    ) == 2
    assert generator.calls == 4
    assert len(telemetry.events) == 6
    assert all(event.latency_ms >= 0 for event in telemetry.events)

    summary = summarize_assistant_telemetry(telemetry.events)
    assert summary.sample_count == 6
    assert summary.outcome_counts == (
        ("answered", 2),
        ("error", 2),
        ("insufficient_evidence", 2),
    )
    assert "rejected" not in dict(summary.outcome_counts)

    payload = summary.to_dict()
    assert payload["outcome_counts"] == {
        "answered": 2,
        "error": 2,
        "insufficient_evidence": 2,
    }
    serialized = repr(payload)
    for sensitive_value in (
        "correlation-id-1",
        "mock_provider_failure",
        "no_evidence",
        str(TENANT_ID),
        "lợi nhuận",
        "FARM-01",
    ):
        assert sensitive_value not in serialized


def _usage() -> AssistantUsage:
    return AssistantUsage(
        promptTokens=12,
        completionTokens=3,
        totalTokens=15,
        promptCacheHitTokens=2,
        promptCacheMissTokens=10,
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
        evidence_id="ev-farm-01",
        title="FARM-01",
        content="Lợi nhuận FARM-01 là 240 triệu đồng.",
        source_type="farm-performance",
        as_of=date(2026, 7, 29),
        tenant_id=TENANT_ID,
        farm_codes=frozenset({"FARM-01"}),
        warehouse_codes=frozenset(),
    )
