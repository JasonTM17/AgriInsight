from __future__ import annotations

from uuid import UUID

import pytest

from agriinsight.analytics_api.assistant_latency_evaluation import (
    ALLOWED_ASSISTANT_OUTCOMES,
    AssistantLatencySummary,
    summarize_assistant_telemetry,
)
from agriinsight.analytics_api.assistant_models import AssistantUsage
from agriinsight.analytics_api.assistant_observability import (
    AssistantTelemetryEvent,
)


TENANT_ID = UUID("20000000-0000-4000-8000-000000000001")


class OutcomeSpoof(str):
    def __eq__(self, other: object) -> bool:
        return other == "answered"


def _usage() -> AssistantUsage:
    return AssistantUsage(
        promptTokens=12,
        completionTokens=3,
        totalTokens=15,
        promptCacheHitTokens=2,
        promptCacheMissTokens=10,
    )


def _event(
    *,
    latency_ms: object = 10,
    outcome: str = "answered",
) -> AssistantTelemetryEvent:
    return AssistantTelemetryEvent(
        correlation_id="correlation-id-sensitive",
        latency_ms=latency_ms,
        outcome=outcome,
        retrieval_count=1,
        refusal_reason=None,
        provider_code="provider-code-sensitive",
        usage=_usage(),
    )


def test_summarize_uses_nearest_rank_percentiles_and_sorted_outcomes() -> None:
    summary = summarize_assistant_telemetry(
        [
            _event(latency_ms=100, outcome="rejected"),
            _event(latency_ms=5, outcome="error"),
            _event(latency_ms=50, outcome="answered"),
            _event(latency_ms=25, outcome="insufficient_evidence"),
            _event(latency_ms=75, outcome="answered"),
            _event(latency_ms=10, outcome="error"),
            _event(latency_ms=90, outcome="answered"),
        ]
    )

    assert ALLOWED_ASSISTANT_OUTCOMES == (
        "answered",
        "insufficient_evidence",
        "rejected",
        "error",
    )
    assert isinstance(summary, AssistantLatencySummary)
    assert summary.sample_count == 7
    assert summary.p50_ms == 50
    assert summary.p95_ms == 100
    assert summary.outcome_counts == (
        ("answered", 3),
        ("error", 2),
        ("insufficient_evidence", 1),
        ("rejected", 1),
    )


@pytest.mark.parametrize(
    ("events", "message"),
    [
        ((), "at least one telemetry event is required"),
        ((object(),), "event must be an AssistantTelemetryEvent"),
        ((_event(latency_ms=True),), "latency_ms must be an integer"),
        ((_event(latency_ms="10"),), "latency_ms must be an integer"),
        ((_event(latency_ms=-1),), "latency_ms must be non-negative"),
        (
            (_event(outcome="unsafe-unknown-outcome"),),
            "outcome must be one of: answered, insufficient_evidence, rejected, error",
        ),
    ],
)
def test_summarize_rejects_invalid_events_with_stable_errors(
    events: tuple[object, ...],
    message: str,
) -> None:
    with pytest.raises(ValueError) as captured:
        summarize_assistant_telemetry(events)

    assert str(captured.value) == message
    assert "unsafe-unknown-outcome" not in str(captured.value)


def test_summarize_rejects_outcome_spoof_before_aggregate_output() -> None:
    with pytest.raises(ValueError) as captured:
        summarize_assistant_telemetry(
            [_event(outcome=OutcomeSpoof("unallowlisted-output-key"))]
        )

    assert str(captured.value) == (
        "outcome must be one of: answered, insufficient_evidence, rejected, error"
    )
    assert "unallowlisted-output-key" not in str(captured.value)


def test_summary_to_dict_contains_only_redacted_aggregate_fields() -> None:
    summary = summarize_assistant_telemetry(
        [
            AssistantTelemetryEvent(
                correlation_id="correlation-id-sensitive",
                latency_ms=42,
                outcome="answered",
                retrieval_count=3,
                refusal_reason="sensitive-refusal-reason",
                provider_code="provider-code-sensitive",
                usage=AssistantUsage(
                    promptTokens=80,
                    completionTokens=20,
                    totalTokens=100,
                    promptCacheHitTokens=30,
                    promptCacheMissTokens=50,
                ),
            )
        ]
    )

    payload = summary.to_dict()

    assert payload == {
        "sample_count": 1,
        "p50_ms": 42,
        "p95_ms": 42,
        "outcome_counts": {"answered": 1},
    }
    assert set(payload) == {"sample_count", "p50_ms", "p95_ms", "outcome_counts"}
    forbidden_field_names = {
        "correlation_id",
        "provider_code",
        "prompt_tokens",
        "evidence",
        "answer",
        "tenant_id",
        "api_key",
    }
    assert not forbidden_field_names.intersection(payload)
    serialized = repr(payload)
    for sensitive_value in (
        "correlation-id-sensitive",
        "provider-code-sensitive",
        "sensitive-refusal-reason",
        "80",
        "20",
        "100",
        str(TENANT_ID),
        "test-only-key-material",
        "private evidence",
        "private answer",
    ):
        assert sensitive_value not in serialized
