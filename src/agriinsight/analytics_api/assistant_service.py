from __future__ import annotations

from time import perf_counter
from typing import Protocol
from uuid import UUID

from agriinsight.analytics_api.assistant_models import (
    AssistantAnswer,
    AssistantQuery,
    AssistantUsage,
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
from agriinsight.analytics_api.auth_scope import AuthorizedScope


class AssistantGenerator(Protocol):
    async def generate(
        self,
        query: AssistantQuery,
        evidence: list[RetrievedEvidence],
        tenant_id: UUID,
    ) -> AssistantAnswer: ...


class AssistantService:
    def __init__(
        self,
        retriever: EvidenceRetriever,
        generator: AssistantGenerator,
        telemetry: AssistantTelemetry | None = None,
    ) -> None:
        self._retriever = retriever
        self._generator = generator
        self._telemetry = telemetry or AssistantTelemetry()

    async def answer(
        self,
        query: AssistantQuery,
        scope: AuthorizedScope,
        corpus: list[EvidenceChunk],
        correlation_id: str = "not-provided",
    ) -> AssistantAnswer:
        started_at = perf_counter()
        evidence = self._retriever.retrieve(query.question, scope, corpus)
        if not evidence:
            answer = AssistantAnswer(
                status="insufficient_evidence",
                answer=(
                    "Không đủ bằng chứng AgriInsight đã xác minh để trả lời "
                    "câu hỏi này."
                ),
                citations=[],
                usage=AssistantUsage(
                    promptTokens=0,
                    completionTokens=0,
                    totalTokens=0,
                    promptCacheHitTokens=0,
                    promptCacheMissTokens=0,
                ),
            )
            self._record(
                answer,
                correlation_id,
                started_at,
                retrieval_count=0,
                refusal_reason="no_evidence",
            )
            return answer
        try:
            answer = await self._generator.generate(
                query,
                evidence,
                scope.tenant_id,
            )
        except Exception as error:
            self._telemetry.record(
                AssistantTelemetryEvent(
                    correlation_id=correlation_id,
                    latency_ms=_elapsed_ms(started_at),
                    outcome="error",
                    retrieval_count=len(evidence),
                    refusal_reason=None,
                    provider_code=getattr(
                        error,
                        "code",
                        "assistant_internal_error",
                    ),
                    usage=_zero_usage(),
                )
            )
            raise
        self._record(
            answer,
            correlation_id,
            started_at,
            retrieval_count=len(evidence),
            refusal_reason=(
                "model_insufficient_evidence"
                if answer.status == "insufficient_evidence"
                else None
            ),
        )
        return answer

    def _record(
        self,
        answer: AssistantAnswer,
        correlation_id: str,
        started_at: float,
        *,
        retrieval_count: int,
        refusal_reason: str | None,
    ) -> None:
        self._telemetry.record(
            AssistantTelemetryEvent(
                correlation_id=correlation_id,
                latency_ms=_elapsed_ms(started_at),
                outcome=answer.status,
                retrieval_count=retrieval_count,
                refusal_reason=refusal_reason,
                provider_code="deepseek-v4-flash" if retrieval_count else None,
                usage=answer.usage,
            )
        )


def _elapsed_ms(started_at: float) -> int:
    return max(0, round((perf_counter() - started_at) * 1_000))


def _zero_usage() -> AssistantUsage:
    return AssistantUsage(
        promptTokens=0,
        completionTokens=0,
        totalTokens=0,
        promptCacheHitTokens=0,
        promptCacheMissTokens=0,
    )
