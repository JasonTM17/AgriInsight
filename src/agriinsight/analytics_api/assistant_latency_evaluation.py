"""Aggregate-only latency evaluation for assistant telemetry events."""

from __future__ import annotations

from dataclasses import dataclass
from math import ceil
from typing import Iterable, Sequence

from agriinsight.analytics_api.assistant_observability import AssistantTelemetryEvent


ALLOWED_ASSISTANT_OUTCOMES = (
    "answered",
    "insufficient_evidence",
    "rejected",
    "error",
)


@dataclass(frozen=True, slots=True)
class AssistantLatencySummary:
    """A deterministic, redacted aggregate over assistant telemetry."""

    sample_count: int
    p50_ms: int
    p95_ms: int
    outcome_counts: tuple[tuple[str, int], ...]

    def to_dict(self) -> dict[str, int | dict[str, int]]:
        """Return only JSON-safe aggregate metrics and outcome counts."""

        return {
            "sample_count": self.sample_count,
            "p50_ms": self.p50_ms,
            "p95_ms": self.p95_ms,
            "outcome_counts": dict(self.outcome_counts),
        }


def summarize_assistant_telemetry(
    events: Iterable[AssistantTelemetryEvent],
) -> AssistantLatencySummary:
    """Validate explicit telemetry events and compute nearest-rank percentiles."""

    latencies: list[int] = []
    outcome_counts: dict[str, int] = {}

    for event in events:
        if type(event) is not AssistantTelemetryEvent:
            raise ValueError("event must be an AssistantTelemetryEvent")

        latency_ms = event.latency_ms
        if type(latency_ms) is not int:
            raise ValueError("latency_ms must be an integer")
        if latency_ms < 0:
            raise ValueError("latency_ms must be non-negative")

        outcome = event.outcome
        if type(outcome) is not str or outcome not in ALLOWED_ASSISTANT_OUTCOMES:
            raise ValueError(
                "outcome must be one of: answered, insufficient_evidence, "
                "rejected, error"
            )

        latencies.append(latency_ms)
        canonical_outcome = next(
            allowed
            for allowed in ALLOWED_ASSISTANT_OUTCOMES
            if outcome == allowed
        )
        outcome_counts[canonical_outcome] = (
            outcome_counts.get(canonical_outcome, 0) + 1
        )

    if not latencies:
        raise ValueError("at least one telemetry event is required")

    sorted_latencies = sorted(latencies)
    return AssistantLatencySummary(
        sample_count=len(sorted_latencies),
        p50_ms=_nearest_rank(sorted_latencies, 0.50),
        p95_ms=_nearest_rank(sorted_latencies, 0.95),
        outcome_counts=tuple(sorted(outcome_counts.items())),
    )


def _nearest_rank(sorted_latencies: Sequence[int], percentile: float) -> int:
    rank = ceil(percentile * len(sorted_latencies)) - 1
    index = min(max(rank, 0), len(sorted_latencies) - 1)
    return sorted_latencies[index]


__all__ = [
    "ALLOWED_ASSISTANT_OUTCOMES",
    "AssistantLatencySummary",
    "summarize_assistant_telemetry",
]
