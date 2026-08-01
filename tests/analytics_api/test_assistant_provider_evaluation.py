from __future__ import annotations

from dataclasses import FrozenInstanceError, replace
import json

import pytest

from agriinsight.analytics_api.assistant_provider_evaluation import (
    ProviderEvaluationObservation,
    ProviderEvaluationSummary,
    summarize_provider_evaluation,
)


SHA = "a" * 40


def _observation(**changes: object) -> ProviderEvaluationObservation:
    values: dict[str, object] = {
        "provider_expected": True,
        "provider_called": True,
        "latency_ms": 100,
        "outcome": "answered",
        "concept_passed": True,
        "expected_citation_count": 2,
        "citation_count": 2,
        "citation_hit_count": 2,
        "prompt_cache_hit_tokens": 0,
        "prompt_cache_miss_tokens": 0,
        "completion_tokens": 0,
        "total_tokens": 0,
    }
    values.update(changes)
    return ProviderEvaluationObservation(**values)  # type: ignore[arg-type]


def _refusal(**changes: object) -> ProviderEvaluationObservation:
    values: dict[str, object] = {
        "provider_expected": False,
        "provider_called": False,
        "latency_ms": 0,
        "outcome": "insufficient_evidence",
        "concept_passed": True,
        "expected_citation_count": 0,
        "citation_count": 0,
        "citation_hit_count": 0,
    }
    values.update(changes)
    return _observation(**values)


def _summarize(
    observations: list[ProviderEvaluationObservation],
    **metadata: object,
) -> ProviderEvaluationSummary:
    values = {
        "source_sha": SHA,
        "fixture_version": "1.0.0",
        "repetitions": 2,
        "concurrency": 3,
    }
    values.update(metadata)
    return summarize_provider_evaluation(observations, **values)  # type: ignore[arg-type]


def test_aggregate_uses_provider_only_nearest_rank_and_exact_cost() -> None:
    observations = [
        _observation(latency_ms=100, prompt_cache_hit_tokens=1_000,
                     prompt_cache_miss_tokens=2_000, completion_tokens=3_000,
                     total_tokens=6_000),
        _observation(latency_ms=5),
        _observation(latency_ms=50),
        _observation(latency_ms=25),
        _observation(latency_ms=75),
        _refusal(),
    ]

    payload = _summarize(observations).to_dict()

    assert payload["provider_p50_completed_response_ms"] == 50
    assert payload["provider_p95_completed_response_ms"] == 100
    assert payload["estimated_cost_usd"] == "0.0011228"
    assert payload["maximum_possible_cost_usd"] == "0.056"
    assert payload["usage"] == {
        "prompt_cache_hit_tokens": 1_000,
        "prompt_cache_miss_tokens": 2_000,
        "completion_tokens": 3_000,
        "total_tokens": 6_000,
    }
    assert payload["closed_corpus"] == {
        "pass_count": 5, "total": 5, "pass_rate": 1.0
    }
    assert payload["citations"] == {
        "hit_count": 10, "total": 10, "precision": 1.0
    }
    assert payload["refusals"] == {
        "pass_count": 1, "total": 1, "precision": 1.0
    }
    assert payload["gates"]["passed"] is True


def test_pricing_snapshot_and_full_run_maximum_are_explicit() -> None:
    summary = _summarize([_observation() for _ in range(20)]).to_dict()

    assert summary["pricing"] == {
        "provider": "deepseek",
        "model": "deepseek-v4-flash",
        "currency": "USD",
        "effective_date": "2026-08-01",
        "source_url": (
            "https://api-docs.deepseek.com/quick_start/pricing/"
            "?article_id=article_1779470751466_8"
        ),
        "retrieved_on": "2026-08-01",
        "unit": "per_million_tokens",
        "prompt_cache_hit_usd_per_million": "0.0028",
        "prompt_cache_miss_usd_per_million": "0.14",
        "completion_usd_per_million": "0.28",
    }
    assert summary["maximum_possible_cost_usd"] == "0.056"


def test_observation_and_summary_are_immutable() -> None:
    observation = _observation()
    summary = _summarize([observation])

    with pytest.raises(FrozenInstanceError):
        observation.latency_ms = 1  # type: ignore[misc]
    with pytest.raises(FrozenInstanceError):
        summary.sample_count = 2  # type: ignore[misc]


@pytest.mark.parametrize(
    ("metadata", "message"),
    [
        ({"source_sha": "A" * 40}, "source_sha must be 40 lowercase hexadecimal characters"),
        ({"source_sha": "a" * 39}, "source_sha must be 40 lowercase hexadecimal characters"),
        ({"fixture_version": "not a safe fixture"}, "fixture_version must be a bounded identifier"),
        ({"repetitions": True}, "repetitions must be a positive integer"),
        ({"concurrency": 0}, "concurrency must be a positive integer"),
    ],
)
def test_metadata_validation_is_strict(metadata: dict[str, object], message: str) -> None:
    with pytest.raises(ValueError, match=f"^{message}$"):
        _summarize([_observation()], **metadata)


@pytest.mark.parametrize(
    ("changes", "message"),
    [
        ({"provider_called": 1}, "provider_called must be a boolean"),
        ({"latency_ms": True}, "latency_ms must be a non-negative integer"),
        ({"outcome": "provider-private-error"}, "outcome must be one of: answered, insufficient_evidence, error"),
        ({"citation_count": -1}, "citation_count must be a non-negative integer"),
        ({"citation_hit_count": 3}, "citation hit count exceeds the validated citation bounds"),
        ({"total_tokens": 1}, "provider token components must equal total_tokens"),
        ({"completion_tokens": 10_001, "total_tokens": 10_001}, "provider call tokens must not exceed 10000"),
    ],
)
def test_observation_validation_rejects_invalid_values(
    changes: dict[str, object], message: str
) -> None:
    with pytest.raises(ValueError, match=f"^{message}$"):
        _summarize([_observation(**changes)])


def test_non_provider_observation_can_report_request_latency_but_not_tokens() -> None:
    assert _summarize([_refusal(latency_ms=1)]).to_dict()["provider_call_count"] == 0
    invalid = _refusal(prompt_cache_miss_tokens=1, total_tokens=1)

    with pytest.raises(
        ValueError, match="^non-provider observations must have zero provider tokens$"
    ):
        _summarize([invalid])


def test_aggregate_token_limit_fails_closed() -> None:
    observations = [
        _observation(prompt_cache_miss_tokens=10_000, total_tokens=10_000)
        for _ in range(21)
    ]

    with pytest.raises(
        ValueError, match="^aggregate provider tokens must not exceed 200000$"
    ):
        _summarize(observations)


def test_failed_semantic_citation_refusal_and_latency_gates_are_derived() -> None:
    observations = [
        _observation(latency_ms=12_001, concept_passed=False,
                     citation_count=1, citation_hit_count=0),
        _refusal(provider_called=True, latency_ms=10),
    ]

    payload = _summarize(observations).to_dict()
    gates = payload["gates"]

    assert payload["closed_corpus"]["pass_rate"] == 0.0
    assert payload["citations"]["precision"] == 0.0
    assert payload["refusals"]["precision"] == 0.0
    assert gates["closed_corpus_pass_rate_1"] is False
    assert gates["citation_precision_1"] is False
    assert gates["refusal_precision_1"] is False
    assert gates["zero_refusal_provider_calls"] is False
    assert gates["provider_p95_within_12000_ms"] is False
    assert gates["passed"] is False


def test_to_dict_contains_only_allowlisted_aggregate_fields() -> None:
    payload = _summarize([_observation(), _refusal()]).to_dict()
    serialized = json.dumps(payload, sort_keys=True)
    forbidden_names = {
        "case_id", "question", "prompt_text", "evidence", "answer", "tenant_id",
        "correlation_id", "provider_code", "provider_diagnostic", "api_key",
    }

    assert not forbidden_names.intersection(payload)
    def keys(value: object) -> set[str]:
        if isinstance(value, dict):
            return {str(key) for key in value} | {
                nested for child in value.values() for nested in keys(child)
            }
        if isinstance(value, list):
            return {nested for child in value for nested in keys(child)}
        return set()

    assert not forbidden_names.intersection(keys(payload))
    for secret in (
        "sensitive-case", "private prompt", "private evidence", "private answer",
        "tenant-sensitive", "correlation-sensitive", "provider traceback",
        "sk-sensitive",
    ):
        assert secret not in serialized


def test_empty_and_non_observation_inputs_are_rejected() -> None:
    with pytest.raises(
        ValueError, match="^at least one provider evaluation observation is required$"
    ):
        _summarize([])
    with pytest.raises(
        ValueError, match="^observation must be a ProviderEvaluationObservation$"
    ):
        summarize_provider_evaluation(
            [object()], source_sha=SHA, fixture_version="1.0.0",
            repetitions=2, concurrency=3,
        )
