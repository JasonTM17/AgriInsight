from __future__ import annotations

import asyncio
from collections import deque
from dataclasses import dataclass, field
from datetime import date, datetime, timezone
from time import monotonic
from uuid import UUID


class AssistantQuotaError(RuntimeError):
    def __init__(self, code: str, safe_message: str) -> None:
        super().__init__(safe_message)
        self.code = code


@dataclass(slots=True)
class _TenantUsage:
    day: date
    committed_tokens: int = 0
    reserved_tokens: int = 0
    request_times: deque[float] = field(default_factory=deque)


class AssistantQuota:
    """Per-process tenant guard; external billing alerts remain mandatory."""

    def __init__(
        self,
        *,
        requests_per_minute: int,
        daily_token_budget: int,
        token_reservation: int,
    ) -> None:
        if requests_per_minute < 1:
            raise ValueError("requests_per_minute must be positive")
        if daily_token_budget < 1:
            raise ValueError("daily_token_budget must be positive")
        if not 1 <= token_reservation <= daily_token_budget:
            raise ValueError("token_reservation must fit the daily budget")
        self._requests_per_minute = requests_per_minute
        self._daily_token_budget = daily_token_budget
        self._token_reservation = token_reservation
        self._lock = asyncio.Lock()
        self._usage: dict[UUID, _TenantUsage] = {}
        self._last_cleanup_day: date | None = None

    async def reserve(self, tenant_id: UUID) -> AssistantQuotaReservation:
        now = monotonic()
        today = datetime.now(timezone.utc).date()
        async with self._lock:
            if self._last_cleanup_day != today:
                self._usage = {
                    tenant_id: usage
                    for tenant_id, usage in self._usage.items()
                    if usage.day == today
                }
                self._last_cleanup_day = today
            usage = self._usage.get(tenant_id)
            if usage is None or usage.day != today:
                usage = _TenantUsage(day=today)
                self._usage[tenant_id] = usage
            while usage.request_times and now - usage.request_times[0] >= 60:
                usage.request_times.popleft()
            if len(usage.request_times) >= self._requests_per_minute:
                raise AssistantQuotaError(
                    "assistant_rate_limited",
                    "The tenant assistant request rate was exceeded.",
                )
            if (
                usage.committed_tokens + usage.reserved_tokens + self._token_reservation
                > self._daily_token_budget
            ):
                raise AssistantQuotaError(
                    "assistant_daily_budget_exhausted",
                    "The tenant assistant daily token budget was exhausted.",
                )
            usage.request_times.append(now)
            usage.reserved_tokens += self._token_reservation
        return AssistantQuotaReservation(
            quota=self,
            tenant_id=tenant_id,
            usage_day=today,
            reserved_tokens=self._token_reservation,
        )

    async def _finalize(
        self,
        tenant_id: UUID,
        usage_day: date,
        reserved_tokens: int,
        consumed_tokens: int,
    ) -> None:
        async with self._lock:
            usage = self._usage.get(tenant_id)
            if usage is None or usage.day != usage_day:
                return
            usage.reserved_tokens = max(
                0,
                usage.reserved_tokens - reserved_tokens,
            )
            usage.committed_tokens += max(0, consumed_tokens)


class AssistantQuotaReservation:
    def __init__(
        self,
        *,
        quota: AssistantQuota,
        tenant_id: UUID,
        usage_day: date,
        reserved_tokens: int,
    ) -> None:
        self._quota = quota
        self._tenant_id = tenant_id
        self._usage_day = usage_day
        self.reserved_tokens = reserved_tokens
        self._finalize_lock = asyncio.Lock()
        self._finalized = False

    async def finalize(self, consumed_tokens: int) -> None:
        async with self._finalize_lock:
            if self._finalized:
                return
            await self._quota._finalize(
                self._tenant_id,
                self._usage_day,
                self.reserved_tokens,
                consumed_tokens,
            )
            self._finalized = True
