"""Fail-closed, aggregate-only metrics for protected provider evaluation."""

from __future__ import annotations

import re
from dataclasses import dataclass
from decimal import Decimal
from math import ceil
from typing import Iterable, Sequence


MAX_TOKENS_PER_PROVIDER_CALL = 10_000
MAX_AGGREGATE_PROVIDER_TOKENS = 200_000
MAX_PROVIDER_P95_MS = 12_000
V4_FLASH_PRICING = (
    ("prompt_cache_hit_usd_per_million", "0.0028"),
    ("prompt_cache_miss_usd_per_million", "0.14"),
    ("completion_usd_per_million", "0.28"),
)
V4_FLASH_PRICING_SOURCE_URL = (
    "https://api-docs.deepseek.com/quick_start/pricing/"
    "?article_id=article_1779470751466_8"
)
V4_FLASH_PRICING_RETRIEVED_ON = "2026-08-01"
_SOURCE_SHA = re.compile(r"^[0-9a-f]{40}$")
_FIXTURE_VERSION = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$")
_OUTCOMES = ("answered", "insufficient_evidence", "error")
_MILLION = Decimal(1_000_000)


@dataclass(frozen=True, slots=True)
class ProviderEvaluationObservation:
    """Content-free result for one closed-corpus evaluation case."""

    provider_expected: bool
    provider_called: bool
    latency_ms: int
    outcome: str
    concept_passed: bool
    expected_citation_count: int
    citation_count: int
    citation_hit_count: int
    prompt_cache_hit_tokens: int
    prompt_cache_miss_tokens: int
    completion_tokens: int
    total_tokens: int


@dataclass(frozen=True, slots=True)
class ProviderEvaluationSummary:
    """Immutable, redacted aggregate suitable for a protected artifact."""

    source_sha: str
    fixture_version: str
    repetitions: int
    concurrency: int
    sample_count: int
    provider_expected_count: int
    provider_call_count: int
    refusal_expected_count: int
    provider_p50_completed_response_ms: int
    provider_p95_completed_response_ms: int
    outcome_counts: tuple[tuple[str, int], ...]
    closed_corpus_pass_count: int
    citation_hit_count: int
    citation_count: int
    refusal_pass_count: int
    prompt_cache_hit_tokens: int
    prompt_cache_miss_tokens: int
    completion_tokens: int
    total_tokens: int
    estimated_cost_usd: str
    maximum_possible_cost_usd: str
    gates: tuple[tuple[str, bool], ...]

    def to_dict(self) -> dict[str, object]:
        """Return only JSON-safe aggregate fields; never per-case content."""

        closed_rate = _rate(
            self.closed_corpus_pass_count, self.provider_expected_count
        )
        citation_precision = _rate(self.citation_hit_count, self.citation_count)
        refusal_precision = _rate(
            self.refusal_pass_count, self.refusal_expected_count
        )
        return {
            "source_sha": self.source_sha,
            "fixture_version": self.fixture_version,
            "repetitions": self.repetitions,
            "concurrency": self.concurrency,
            "sample_count": self.sample_count,
            "provider_expected_count": self.provider_expected_count,
            "provider_call_count": self.provider_call_count,
            "refusal_expected_count": self.refusal_expected_count,
            "provider_p50_completed_response_ms": (
                self.provider_p50_completed_response_ms
            ),
            "provider_p95_completed_response_ms": (
                self.provider_p95_completed_response_ms
            ),
            "outcome_counts": dict(self.outcome_counts),
            "closed_corpus": {
                "pass_count": self.closed_corpus_pass_count,
                "total": self.provider_expected_count,
                "pass_rate": closed_rate,
            },
            "citations": {
                "hit_count": self.citation_hit_count,
                "total": self.citation_count,
                "precision": citation_precision,
            },
            "refusals": {
                "pass_count": self.refusal_pass_count,
                "total": self.refusal_expected_count,
                "precision": refusal_precision,
            },
            "usage": {
                "prompt_cache_hit_tokens": self.prompt_cache_hit_tokens,
                "prompt_cache_miss_tokens": self.prompt_cache_miss_tokens,
                "completion_tokens": self.completion_tokens,
                "total_tokens": self.total_tokens,
            },
            "pricing": {
                "provider": "deepseek",
                "model": "deepseek-v4-flash",
                "currency": "USD",
                "effective_date": "2026-08-01",
                "source_url": V4_FLASH_PRICING_SOURCE_URL,
                "retrieved_on": V4_FLASH_PRICING_RETRIEVED_ON,
                "unit": "per_million_tokens",
                **dict(V4_FLASH_PRICING),
            },
            "estimated_cost_usd": self.estimated_cost_usd,
            "maximum_possible_cost_usd": self.maximum_possible_cost_usd,
            "gates": dict(self.gates),
        }


def summarize_provider_evaluation(
    observations: Iterable[ProviderEvaluationObservation],
    *,
    source_sha: str,
    fixture_version: str,
    repetitions: int,
    concurrency: int,
) -> ProviderEvaluationSummary:
    """Validate content-free observations and derive all release gates."""

    _validate_metadata(source_sha, fixture_version, repetitions, concurrency)
    values = tuple(observations)
    if not values:
        raise ValueError("at least one provider evaluation observation is required")
    for observation in values:
        _validate_observation(observation)

    total_tokens = sum(item.total_tokens for item in values)
    if total_tokens > MAX_AGGREGATE_PROVIDER_TOKENS:
        raise ValueError("aggregate provider tokens must not exceed 200000")
    provider_values = tuple(item for item in values if item.provider_called)
    provider_expected = tuple(item for item in values if item.provider_expected)
    refusals = tuple(item for item in values if not item.provider_expected)
    latencies = sorted(item.latency_ms for item in provider_values)
    p50 = _nearest_rank(latencies, 0.50) if latencies else 0
    p95 = _nearest_rank(latencies, 0.95) if latencies else 0
    closed_passes = sum(_closed_corpus_pass(item) for item in provider_expected)
    refusal_passes = sum(_refusal_pass(item) for item in refusals)
    citation_count = sum(item.citation_count for item in provider_expected)
    citation_hits = sum(item.citation_hit_count for item in provider_expected)
    outcome_counts = tuple(
        sorted((outcome, sum(item.outcome == outcome for item in values))
               for outcome in _OUTCOMES if any(item.outcome == outcome for item in values))
    )
    gates = (
        ("zero_provider_errors", not any(item.outcome == "error" for item in provider_values)),
        ("closed_corpus_pass_rate_1", closed_passes == len(provider_expected)),
        ("citation_precision_1", citation_count > 0 and citation_hits == citation_count),
        ("refusal_precision_1", refusal_passes == len(refusals)),
        ("zero_refusal_provider_calls", not any(item.provider_called for item in refusals)),
        ("per_call_tokens_within_limit", True),
        ("aggregate_tokens_within_limit", True),
        ("provider_p95_within_12000_ms", bool(latencies) and p95 <= MAX_PROVIDER_P95_MS),
    )
    gates += (("passed", all(passed for _, passed in gates)),)
    hit = sum(item.prompt_cache_hit_tokens for item in values)
    miss = sum(item.prompt_cache_miss_tokens for item in values)
    completion = sum(item.completion_tokens for item in values)
    cost = _cost(hit, miss, completion)
    maximum_cost = (
        Decimal(MAX_AGGREGATE_PROVIDER_TOKENS) * Decimal("0.28") / _MILLION
    )
    return ProviderEvaluationSummary(
        source_sha, fixture_version, repetitions, concurrency, len(values),
        len(provider_expected), len(provider_values), len(refusals), p50, p95,
        outcome_counts, closed_passes, citation_hits, citation_count,
        refusal_passes, hit, miss, completion, total_tokens,
        _decimal_text(cost), _decimal_text(maximum_cost), gates,
    )


def _validate_metadata(source_sha: object, fixture_version: object,
                       repetitions: object, concurrency: object) -> None:
    if type(source_sha) is not str or not _SOURCE_SHA.fullmatch(source_sha):
        raise ValueError("source_sha must be 40 lowercase hexadecimal characters")
    if type(fixture_version) is not str or not _FIXTURE_VERSION.fullmatch(fixture_version):
        raise ValueError("fixture_version must be a bounded identifier")
    for name, value in (("repetitions", repetitions), ("concurrency", concurrency)):
        if type(value) is not int or value <= 0:
            raise ValueError(f"{name} must be a positive integer")


def _validate_observation(item: object) -> None:
    if type(item) is not ProviderEvaluationObservation:
        raise ValueError("observation must be a ProviderEvaluationObservation")
    for name in ("provider_expected", "provider_called", "concept_passed"):
        if type(getattr(item, name)) is not bool:
            raise ValueError(f"{name} must be a boolean")
    if type(item.outcome) is not str or item.outcome not in _OUTCOMES:
        raise ValueError("outcome must be one of: answered, insufficient_evidence, error")
    count_names = (
        "latency_ms", "expected_citation_count", "citation_count",
        "citation_hit_count", "prompt_cache_hit_tokens",
        "prompt_cache_miss_tokens", "completion_tokens", "total_tokens",
    )
    for name in count_names:
        value = getattr(item, name)
        if type(value) is not int or value < 0:
            raise ValueError(f"{name} must be a non-negative integer")
    if item.citation_hit_count > min(item.citation_count, item.expected_citation_count):
        raise ValueError("citation hit count exceeds the validated citation bounds")
    calculated = (item.prompt_cache_hit_tokens
                  + item.prompt_cache_miss_tokens + item.completion_tokens)
    if calculated != item.total_tokens:
        raise ValueError("provider token components must equal total_tokens")
    if item.total_tokens > MAX_TOKENS_PER_PROVIDER_CALL:
        raise ValueError("provider call tokens must not exceed 10000")
    if not item.provider_called and item.total_tokens != 0:
        raise ValueError("non-provider observations must have zero provider tokens")


def _closed_corpus_pass(item: ProviderEvaluationObservation) -> bool:
    return (item.provider_called and item.outcome == "answered"
            and item.concept_passed and item.citation_count > 0
            and item.citation_hit_count == item.citation_count)


def _refusal_pass(item: ProviderEvaluationObservation) -> bool:
    return (not item.provider_called and item.outcome == "insufficient_evidence"
            and item.citation_count == 0 and item.total_tokens == 0)


def _nearest_rank(values: Sequence[int], percentile: float) -> int:
    return values[min(max(ceil(percentile * len(values)) - 1, 0), len(values) - 1)]


def _rate(numerator: int, denominator: int) -> float:
    return numerator / denominator if denominator else 0.0


def _cost(hit: int, miss: int, completion: int) -> Decimal:
    return (Decimal(hit) * Decimal("0.0028")
            + Decimal(miss) * Decimal("0.14")
            + Decimal(completion) * Decimal("0.28")) / _MILLION


def _decimal_text(value: Decimal) -> str:
    return format(value.normalize(), "f")


__all__ = ["ProviderEvaluationObservation", "ProviderEvaluationSummary",
           "summarize_provider_evaluation"]
