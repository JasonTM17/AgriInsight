from __future__ import annotations

import asyncio
import json
from datetime import date
from pathlib import Path
import sys
from uuid import UUID


_PROJECT_SOURCE = Path(__file__).resolve().parents[1] / "src"
if str(_PROJECT_SOURCE) not in sys.path:
    sys.path.insert(0, str(_PROJECT_SOURCE))

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
    AssistantTelemetry,
    AssistantTelemetryEvent,
)
from agriinsight.analytics_api.assistant_retrieval import (
    EvidenceChunk,
    EvidenceRetriever,
    RetrievedEvidence,
)
from agriinsight.analytics_api.assistant_service import AssistantService
from agriinsight.analytics_api.auth_scope import AuthorizedScope


_TENANT_ID = UUID("40000000-0000-4000-8000-000000000001")
_EXPECTED_OUTCOME_COUNTS = {
    "answered": 2,
    "error": 2,
    "insufficient_evidence": 2,
}
_ANSWERED_QUESTIONS = ("FARM-01 seasonal output", "FARM-01 seasonal trend")
_ERROR_QUESTIONS = ("FARM-01 generation check one", "FARM-01 generation check two")


class CapturingTelemetry(AssistantTelemetry):
    """Keep real service telemetry in memory without emitting a log record."""

    def __init__(self) -> None:
        self.events: list[AssistantTelemetryEvent] = []

    def record(self, event: AssistantTelemetryEvent) -> None:
        self.events.append(event)


class InMemoryGenerationFailure(RuntimeError):
    """Exercise the service error path without reaching a provider."""

    code = "mock_provider_failure"


class InMemoryGenerator:
    def __init__(self, tenant_id: UUID) -> None:
        self._tenant_id = tenant_id
        self._active_requests = 0
        self.max_active_requests = 0

    async def generate(
        self,
        query: AssistantQuery,
        evidence: list[RetrievedEvidence],
        tenant_id: UUID,
    ) -> AssistantAnswer:
        if tenant_id != self._tenant_id:
            raise RuntimeError("mock workload tenant mismatch")

        self._active_requests += 1
        self.max_active_requests = max(
            self.max_active_requests,
            self._active_requests,
        )
        try:
            delay = 0.006 if query.question in _ANSWERED_QUESTIONS else 0.009
            await asyncio.sleep(delay)
            if query.question in _ERROR_QUESTIONS:
                raise InMemoryGenerationFailure()
        finally:
            self._active_requests -= 1

        chunk = evidence[0].chunk
        return AssistantAnswer(
            status="answered",
            answer="Verified local result.",
            citations=[
                EvidenceCitation(
                    evidenceId=chunk.evidence_id,
                    title=chunk.title,
                    excerpt=chunk.content,
                    sourceType=chunk.source_type,
                    asOf=chunk.as_of,
                )
            ],
            usage=AssistantUsage(
                promptTokens=12,
                completionTokens=4,
                totalTokens=16,
                promptCacheHitTokens=0,
                promptCacheMissTokens=12,
            ),
        )


def _scope() -> AuthorizedScope:
    return AuthorizedScope(
        farm_codes=frozenset({"FARM-01"}),
        farm_tenant_wide=False,
        tenant_id=_TENANT_ID,
        warehouse_codes=frozenset(),
        warehouse_tenant_wide=False,
    )


def _corpus() -> list[EvidenceChunk]:
    return [
        EvidenceChunk(
            evidence_id="farm-01-seasonal-output",
            title="FARM-01 seasonal output",
            content="Verified seasonal output for FARM-01.",
            source_type="farm-performance",
            as_of=date(2026, 7, 29),
            tenant_id=_TENANT_ID,
            farm_codes=frozenset({"FARM-01"}),
            warehouse_codes=frozenset(),
        )
    ]


async def _run_workload():
    telemetry = CapturingTelemetry()
    generator = InMemoryGenerator(_TENANT_ID)
    service = AssistantService(
        EvidenceRetriever(max_items=5, max_characters=1_000),
        generator,
        telemetry,
    )
    scope = _scope()
    corpus = _corpus()
    questions = (
        _ANSWERED_QUESTIONS[0],
        _ANSWERED_QUESTIONS[1],
        _ERROR_QUESTIONS[0],
        "unrelated local question one",
        "unrelated local question two",
        _ERROR_QUESTIONS[1],
    )
    concurrency = asyncio.Semaphore(3)

    async def invoke(question: str, request_number: int) -> None:
        async with concurrency:
            try:
                await service.answer(
                    AssistantQuery(question=question),
                    scope,
                    corpus,
                    correlation_id=f"local-evaluation-{request_number}",
                )
            except InMemoryGenerationFailure:
                pass

    await asyncio.gather(
        *(invoke(question, index) for index, question in enumerate(questions, start=1))
    )
    if generator.max_active_requests != 3:
        raise RuntimeError("mock workload did not reach configured concurrency")
    return summarize_assistant_telemetry(telemetry.events)


def main() -> None:
    try:
        summary = asyncio.run(_run_workload())
        payload = summary.to_dict()
    except Exception:
        raise SystemExit("assistant latency evaluation failed") from None

    if payload.get("outcome_counts") != _EXPECTED_OUTCOME_COUNTS:
        raise SystemExit("assistant latency evaluation failed: unexpected aggregate outcomes")

    print(json.dumps(payload, sort_keys=True, separators=(",", ":")))


if __name__ == "__main__":
    main()
