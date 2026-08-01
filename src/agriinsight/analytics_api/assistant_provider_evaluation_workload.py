"""Closed-corpus live-provider workload for the protected assistant gate."""

from __future__ import annotations

import asyncio
import json
import unicodedata
from contextvars import ContextVar
from dataclasses import dataclass, replace
from datetime import date
from pathlib import Path
from time import perf_counter
from typing import Any
from uuid import UUID

import httpx

from agriinsight.analytics_api.assistant_models import AssistantAnswer, AssistantQuery
from agriinsight.analytics_api.assistant_observability import (
    AssistantTelemetry,
)
from agriinsight.analytics_api.assistant_provider_evaluation import (
    ProviderEvaluationObservation,
    ProviderEvaluationSummary,
    summarize_provider_evaluation,
)
from agriinsight.analytics_api.assistant_quota import AssistantQuota
from agriinsight.analytics_api.assistant_retrieval import (
    EvidenceChunk,
    EvidenceRetriever,
    RetrievedEvidence,
)
from agriinsight.analytics_api.assistant_service import AssistantService
from agriinsight.analytics_api.assistant_settings import AssistantSettings
from agriinsight.analytics_api.auth_scope import AuthorizedScope
from agriinsight.analytics_api.deepseek_assistant_client import (
    DeepSeekAssistantClient,
    _request_payload,
)


FIXTURE = Path(__file__).parents[3] / "tests" / "fixtures" / "assistant-retrieval-evaluation-v1.json"
TENANT_ID = UUID("20000000-0000-4000-8000-000000000001")
SOURCE_SHA_LENGTH = 40
REQUEST_BYTE_LIMIT = 8_000
OUTPUT_TOKEN_LIMIT = 512
PER_CALL_TOKEN_LIMIT = 10_000
AGGREGATE_TOKEN_LIMIT = 200_000
REPETITIONS = 2
CONCURRENCY = 3
_ACTIVE_REQUEST_KEY: ContextVar[tuple[int, int] | None] = ContextVar(
    "assistant_provider_evaluation_request_key", default=None
)


class _CapturingTelemetry(AssistantTelemetry):
    """Keep service telemetry in memory; the default logger is not safe here."""

    def record(self, event: Any) -> None:
        return None


@dataclass(frozen=True, slots=True)
class _ProviderDispatch:
    """Content-free timing for one provider generator dispatch."""

    called: bool
    latency_ms: int


class _ProviderUsageLimitError(RuntimeError):
    """Stop the run before any later batch can spend provider tokens."""


class _LatencyCapturingGenerator:
    """Measure only the buffered provider boundary without emitting telemetry."""

    def __init__(self, client: DeepSeekAssistantClient) -> None:
        self._client = client
        self._dispatches: dict[tuple[int, int], _ProviderDispatch] = {}

    async def generate(
        self,
        query: AssistantQuery,
        evidence: list[RetrievedEvidence],
        tenant_id: UUID,
    ) -> AssistantAnswer:
        request_key = _ACTIVE_REQUEST_KEY.get()
        if request_key is None:
            raise RuntimeError("provider evaluation request context is required")
        started = perf_counter()
        try:
            return await self._client.generate(query, evidence, tenant_id)
        finally:
            self._dispatches[request_key] = _ProviderDispatch(
                called=True,
                latency_ms=_elapsed_ms(started),
            )

    def take_dispatch(self, request_key: tuple[int, int]) -> _ProviderDispatch:
        return self._dispatches.pop(request_key, _ProviderDispatch(False, 0))


async def run_provider_evaluation(
    settings: AssistantSettings,
    *,
    source_sha: str,
    fixture_path: Path = FIXTURE,
    repetitions: int = REPETITIONS,
    concurrency: int = CONCURRENCY,
    http_client: httpx.AsyncClient | None = None,
) -> ProviderEvaluationSummary:
    _validate_run_inputs(source_sha, repetitions, concurrency)
    payload = json.loads(fixture_path.read_text(encoding="utf-8"))
    corpus = [_chunk(item) for item in payload["corpus"]]
    cases = payload["cases"]
    scope = AuthorizedScope(
        farm_codes=frozenset({"FARM-01", "FARM-02"}),
        farm_tenant_wide=True,
        tenant_id=TENANT_ID,
        warehouse_codes=frozenset({"WH-01"}),
        warehouse_tenant_wide=True,
    )
    retriever = EvidenceRetriever(max_items=5, max_characters=12_000)
    evaluation_settings = replace(
        settings.validated(),
        max_output_tokens=OUTPUT_TOKEN_LIMIT,
        max_concurrent_requests=concurrency,
        daily_token_budget=AGGREGATE_TOKEN_LIMIT,
        token_reservation=PER_CALL_TOKEN_LIMIT,
    ).validated()
    _preflight_requests(evaluation_settings, retriever, scope, corpus, cases)

    client = http_client or httpx.AsyncClient(base_url=evaluation_settings.base_url)
    close_client = http_client is None
    try:
        generator = _LatencyCapturingGenerator(
            DeepSeekAssistantClient(evaluation_settings, client)
        )
        service = AssistantService(
            retriever,
            generator,
            telemetry=_CapturingTelemetry(),
            quota=AssistantQuota(
                requests_per_minute=30,
                daily_token_budget=AGGREGATE_TOKEN_LIMIT,
                token_reservation=PER_CALL_TOKEN_LIMIT,
            ),
        )
        observations: list[ProviderEvaluationObservation] = []
        requests = [
            (case, repetition, request_number)
            for repetition in range(repetitions)
            for request_number, case in enumerate(cases, start=1)
        ]
        for batch_start in range(0, len(requests), concurrency):
            batch = requests[batch_start : batch_start + concurrency]
            tasks = [
                asyncio.create_task(
                    _run_case(
                        service,
                        generator,
                        case,
                        scope,
                        corpus,
                        repetition,
                        request_number,
                    )
                )
                for case, repetition, request_number in batch
            ]
            try:
                observations.extend(await asyncio.gather(*tasks))
            except BaseException:
                for task in tasks:
                    task.cancel()
                await asyncio.gather(*tasks, return_exceptions=True)
                raise
            if sum(item.total_tokens for item in observations) > AGGREGATE_TOKEN_LIMIT:
                raise RuntimeError("assistant provider aggregate token ceiling exceeded")
        return summarize_provider_evaluation(
            observations,
            source_sha=source_sha,
            fixture_version=str(payload["version"]),
            repetitions=repetitions,
            concurrency=concurrency,
        )
    finally:
        if close_client:
            await client.aclose()


async def _run_case(
    service: AssistantService,
    generator: _LatencyCapturingGenerator,
    case: dict[str, Any],
    scope: AuthorizedScope,
    corpus: list[EvidenceChunk],
    repetition: int,
    request_number: int,
) -> ProviderEvaluationObservation:
    query = AssistantQuery(question=case["question"])
    request_key = (repetition, request_number)
    context_token = _ACTIVE_REQUEST_KEY.set(request_key)
    try:
        answer = await service.answer(
            query,
            scope,
            corpus,
            correlation_id=f"provider-evaluation-{repetition}-{request_number}",
        )
    except Exception:
        dispatch = generator.take_dispatch(request_key)
        return ProviderEvaluationObservation(
            provider_expected=bool(case["shouldRetrieve"]),
            provider_called=dispatch.called,
            latency_ms=dispatch.latency_ms,
            outcome="error",
            concept_passed=False,
            expected_citation_count=len(case["expectedEvidenceIds"]),
            citation_count=0,
            citation_hit_count=0,
            prompt_cache_hit_tokens=0,
            prompt_cache_miss_tokens=0,
            completion_tokens=0,
            total_tokens=0,
        )
    finally:
        _ACTIVE_REQUEST_KEY.reset(context_token)

    dispatch = generator.take_dispatch(request_key)
    _validate_provider_usage(answer)
    citations = [item.evidence_id for item in answer.citations]
    expected = set(case["expectedEvidenceIds"])
    return ProviderEvaluationObservation(
        provider_expected=bool(case["shouldRetrieve"]),
        provider_called=dispatch.called,
        latency_ms=dispatch.latency_ms,
        outcome=answer.status,
        concept_passed=_concepts_match(answer.answer, case["expectedAnswerConcepts"]),
        expected_citation_count=len(expected),
        citation_count=len(citations),
        citation_hit_count=len(set(citations) & expected),
        prompt_cache_hit_tokens=answer.usage.prompt_cache_hit_tokens,
        prompt_cache_miss_tokens=answer.usage.prompt_cache_miss_tokens,
        completion_tokens=answer.usage.completion_tokens,
        total_tokens=answer.usage.total_tokens,
    )


def _preflight_requests(
    settings: AssistantSettings,
    retriever: EvidenceRetriever,
    scope: AuthorizedScope,
    corpus: list[EvidenceChunk],
    cases: list[dict[str, Any]],
) -> None:
    for case in cases:
        if not case["shouldRetrieve"]:
            continue
        evidence = retriever.retrieve(case["question"], scope, corpus)
        request = _request_payload(
            AssistantQuery(question=case["question"]),
            evidence,
            TENANT_ID,
            settings,
        )
        request_bytes = len(
            json.dumps(request, ensure_ascii=False, separators=(",", ":")).encode()
        )
        if request_bytes > REQUEST_BYTE_LIMIT:
            raise RuntimeError("assistant provider request preflight exceeded byte ceiling")


def _chunk(item: dict[str, Any]) -> EvidenceChunk:
    return EvidenceChunk(
        evidence_id=item["evidenceId"],
        title=item["title"],
        content=item["content"],
        source_type=item["sourceType"],
        as_of=date.fromisoformat(item["asOf"]),
        tenant_id=UUID(item["tenantId"]),
        farm_codes=frozenset(item["farmCodes"]),
        warehouse_codes=frozenset(item["warehouseCodes"]),
        tenant_wide_only=item["tenantWideOnly"],
    )


def _concepts_match(answer: str, concepts: list[list[str]]) -> bool:
    answer_tokens = _normalize(answer).split()
    return all(
        any(_contains_token_phrase(answer_tokens, _normalize(value).split()) for value in group)
        for group in concepts
    )


def _contains_token_phrase(tokens: list[str], phrase: list[str]) -> bool:
    if not phrase or len(phrase) > len(tokens):
        return False
    return any(
        tokens[offset : offset + len(phrase)] == phrase
        for offset in range(len(tokens) - len(phrase) + 1)
    )


def _validate_provider_usage(answer: AssistantAnswer) -> None:
    if (
        answer.usage.completion_tokens > OUTPUT_TOKEN_LIMIT
        or answer.usage.total_tokens > PER_CALL_TOKEN_LIMIT
    ):
        raise _ProviderUsageLimitError(
            "assistant provider response exceeded evaluation usage limits"
        )


def _normalize(value: str) -> str:
    decomposed = unicodedata.normalize("NFKD", value.casefold())
    without_marks = "".join(
        character for character in decomposed if unicodedata.category(character) != "Mn"
    )
    return " ".join(without_marks.split())


def _validate_run_inputs(source_sha: str, repetitions: int, concurrency: int) -> None:
    if len(source_sha) != SOURCE_SHA_LENGTH or any(
        character not in "0123456789abcdef" for character in source_sha
    ):
        raise ValueError("source_sha must be a 40-character lowercase hexadecimal SHA")
    if repetitions != REPETITIONS or concurrency != CONCURRENCY:
        raise ValueError("provider evaluation workload coordinates are fixed")


def _elapsed_ms(started: float) -> int:
    return max(0, round((perf_counter() - started) * 1_000))
