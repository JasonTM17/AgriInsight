from __future__ import annotations

import logging
from dataclasses import dataclass

from agriinsight.analytics_api.assistant_models import AssistantUsage


@dataclass(frozen=True, slots=True)
class AssistantTelemetryEvent:
    correlation_id: str
    latency_ms: int
    outcome: str
    retrieval_count: int
    refusal_reason: str | None
    provider_code: str | None
    usage: AssistantUsage


class AssistantTelemetry:
    """Emit allowlisted operational fields without prompts or evidence."""

    def __init__(self, logger: logging.Logger | None = None) -> None:
        self._logger = logger or logging.getLogger(
            "agriinsight.analytics.assistant"
        )

    def record(self, event: AssistantTelemetryEvent) -> None:
        self._logger.info(
            "assistant_query",
            extra={
                "assistant_event": {
                    "completion_tokens": event.usage.completion_tokens,
                    "correlation_id": event.correlation_id,
                    "latency_ms": event.latency_ms,
                    "outcome": event.outcome,
                    "prompt_cache_hit_tokens": (
                        event.usage.prompt_cache_hit_tokens
                    ),
                    "prompt_cache_miss_tokens": (
                        event.usage.prompt_cache_miss_tokens
                    ),
                    "prompt_tokens": event.usage.prompt_tokens,
                    "provider_code": event.provider_code,
                    "refusal_reason": event.refusal_reason,
                    "retrieval_count": event.retrieval_count,
                    "total_tokens": event.usage.total_tokens,
                }
            },
        )
