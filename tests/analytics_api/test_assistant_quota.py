from __future__ import annotations

import asyncio
from uuid import UUID

import pytest

from agriinsight.analytics_api.assistant_quota import (
    AssistantQuota,
    AssistantQuotaError,
)


TENANT_ID = UUID("20000000-0000-4000-8000-000000000001")


def test_request_rate_limit_rejects_before_reserving_more_tokens() -> None:
    async def exercise() -> None:
        quota = AssistantQuota(
            requests_per_minute=1,
            daily_token_budget=20_000,
            token_reservation=10_000,
        )
        reservation = await quota.reserve(TENANT_ID)
        await reservation.finalize(100)
        with pytest.raises(
            AssistantQuotaError,
            match="request rate",
        ) as captured:
            await quota.reserve(TENANT_ID)
        assert captured.value.code == "assistant_rate_limited"

    asyncio.run(exercise())


def test_daily_budget_accounts_for_committed_and_concurrent_reservations() -> None:
    async def exercise() -> None:
        quota = AssistantQuota(
            requests_per_minute=10,
            daily_token_budget=20_000,
            token_reservation=10_000,
        )
        first = await quota.reserve(TENANT_ID)
        second = await quota.reserve(TENANT_ID)
        with pytest.raises(AssistantQuotaError) as captured:
            await quota.reserve(TENANT_ID)
        assert captured.value.code == "assistant_daily_budget_exhausted"

        await first.finalize(2_000)
        await second.finalize(3_000)
        third = await quota.reserve(TENANT_ID)
        await third.finalize(1_000)

    asyncio.run(exercise())


def test_reservation_finalization_is_concurrently_idempotent() -> None:
    async def exercise() -> None:
        quota = AssistantQuota(
            requests_per_minute=10,
            daily_token_budget=11_000,
            token_reservation=10_000,
        )
        reservation = await quota.reserve(TENANT_ID)

        await asyncio.gather(
            reservation.finalize(1_000),
            reservation.finalize(1_000),
        )

        next_reservation = await quota.reserve(TENANT_ID)
        await next_reservation.finalize(0)

    asyncio.run(exercise())
