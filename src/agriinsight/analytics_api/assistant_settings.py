from __future__ import annotations

import math
import re
from dataclasses import dataclass, field, replace
from typing import Mapping
from urllib.parse import urlsplit


class AssistantSettingsError(ValueError):
    """Raised when the assistant provider boundary is not fail-closed."""


@dataclass(frozen=True, slots=True)
class AssistantSettings:
    enabled: bool = False
    provider: str = "deepseek"
    base_url: str = "https://api.deepseek.com"
    model: str = "deepseek-v4-flash"
    api_key: str = field(default="", repr=False)
    thinking_enabled: bool = False
    connect_timeout_seconds: float = 3.0
    read_timeout_seconds: float = 25.0
    queue_timeout_seconds: float = 2.0
    max_evidence_items: int = 8
    max_evidence_chars: int = 12_000
    max_output_tokens: int = 1_200
    max_concurrent_requests: int = 8
    requests_per_minute: int = 30
    daily_token_budget: int = 1_000_000
    token_reservation: int = 10_000

    @classmethod
    def from_environment(cls, source: Mapping[str, str]) -> AssistantSettings:
        return cls(
            enabled=_boolean(source, "AGRIINSIGHT_ASSISTANT_ENABLED", False),
            provider=source.get("AGRIINSIGHT_LLM_PROVIDER", "deepseek").strip(),
            base_url=source.get(
                "AGRIINSIGHT_LLM_BASE_URL", "https://api.deepseek.com"
            ).strip(),
            model=source.get("AGRIINSIGHT_LLM_MODEL", "deepseek-v4-flash").strip(),
            api_key=source.get("AGRIINSIGHT_LLM_API_KEY", "").strip(),
            thinking_enabled=_boolean(
                source, "AGRIINSIGHT_LLM_THINKING_ENABLED", False
            ),
            connect_timeout_seconds=_float(
                source, "AGRIINSIGHT_LLM_CONNECT_TIMEOUT_SECONDS", 3.0
            ),
            read_timeout_seconds=_float(
                source, "AGRIINSIGHT_LLM_READ_TIMEOUT_SECONDS", 25.0
            ),
            queue_timeout_seconds=_float(
                source, "AGRIINSIGHT_LLM_QUEUE_TIMEOUT_SECONDS", 2.0
            ),
            max_evidence_items=_integer(
                source, "AGRIINSIGHT_ASSISTANT_MAX_EVIDENCE_ITEMS", 8
            ),
            max_evidence_chars=_integer(
                source, "AGRIINSIGHT_ASSISTANT_MAX_EVIDENCE_CHARS", 12_000
            ),
            max_output_tokens=_integer(
                source, "AGRIINSIGHT_ASSISTANT_MAX_OUTPUT_TOKENS", 1_200
            ),
            max_concurrent_requests=_integer(
                source, "AGRIINSIGHT_ASSISTANT_MAX_CONCURRENT_REQUESTS", 8
            ),
            requests_per_minute=_integer(
                source, "AGRIINSIGHT_ASSISTANT_REQUESTS_PER_MINUTE", 30
            ),
            daily_token_budget=_integer(
                source, "AGRIINSIGHT_ASSISTANT_DAILY_TOKEN_BUDGET", 1_000_000
            ),
            token_reservation=_integer(
                source, "AGRIINSIGHT_ASSISTANT_TOKEN_RESERVATION", 10_000
            ),
        ).validated()

    def validated(self) -> AssistantSettings:
        if self.provider != "deepseek":
            raise AssistantSettingsError("Assistant provider must be DeepSeek")
        if self.model != "deepseek-v4-flash":
            raise AssistantSettingsError("Assistant model must be DeepSeek V4 Flash")
        base_url = _validated_base_url(self.base_url)
        api_key = self.api_key.strip()
        if self.enabled and not api_key:
            raise AssistantSettingsError("DeepSeek API key is required")
        if api_key and not re.fullmatch(r"[A-Za-z0-9._-]{20,256}", api_key):
            raise AssistantSettingsError("DeepSeek API key has an invalid format")
        _bounded_float(
            self.connect_timeout_seconds,
            "connect timeout",
            minimum=0.5,
            maximum=10.0,
        )
        _bounded_float(
            self.read_timeout_seconds,
            "read timeout",
            minimum=1.0,
            maximum=120.0,
        )
        _bounded_float(
            self.queue_timeout_seconds,
            "queue timeout",
            minimum=0.1,
            maximum=10.0,
        )
        _bounded_int(self.max_evidence_items, "evidence items", 1, 20)
        _bounded_int(self.max_evidence_chars, "evidence characters", 2_048, 50_000)
        _bounded_int(self.max_output_tokens, "output token budget", 128, 4_096)
        _bounded_int(self.max_concurrent_requests, "concurrent requests", 1, 32)
        _bounded_int(self.requests_per_minute, "requests per minute", 1, 600)
        _bounded_int(
            self.daily_token_budget,
            "daily token budget",
            10_000,
            100_000_000,
        )
        _bounded_int(
            self.token_reservation,
            "token reservation",
            1_000,
            100_000,
        )
        if self.token_reservation > self.daily_token_budget:
            raise AssistantSettingsError(
                "Assistant token reservation must not exceed daily budget"
            )
        return replace(self, base_url=base_url, api_key=api_key)


def _validated_base_url(value: str) -> str:
    parsed = urlsplit(value)
    if (
        parsed.scheme != "https"
        or parsed.hostname != "api.deepseek.com"
        or parsed.port is not None
        or parsed.username
        or parsed.password
        or parsed.query
        or parsed.fragment
        or parsed.path not in {"", "/"}
    ):
        raise AssistantSettingsError("LLM base URL must be the fixed DeepSeek origin")
    return "https://api.deepseek.com"


def _boolean(source: Mapping[str, str], key: str, default: bool) -> bool:
    raw = source.get(key)
    if raw is None:
        return default
    normalized = raw.strip().lower()
    if normalized == "true":
        return True
    if normalized == "false":
        return False
    raise AssistantSettingsError(f"{key} must be true or false")


def _integer(source: Mapping[str, str], key: str, default: int) -> int:
    try:
        return int(source.get(key, str(default)))
    except ValueError as error:
        raise AssistantSettingsError(f"{key} must be an integer") from error


def _float(source: Mapping[str, str], key: str, default: float) -> float:
    try:
        return float(source.get(key, str(default)))
    except ValueError as error:
        raise AssistantSettingsError(f"{key} must be numeric") from error


def _bounded_int(value: int, label: str, minimum: int, maximum: int) -> None:
    if not minimum <= value <= maximum:
        raise AssistantSettingsError(
            f"Assistant {label} must be between {minimum} and {maximum}"
        )


def _bounded_float(value: float, label: str, minimum: float, maximum: float) -> None:
    if not math.isfinite(value) or not minimum <= value <= maximum:
        raise AssistantSettingsError(
            f"Assistant {label} must be between {minimum} and {maximum}"
        )
