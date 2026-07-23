from __future__ import annotations

import asyncio
import json
import random
from typing import Any
from uuid import UUID

import httpx
from pydantic import ValidationError

from agriinsight.analytics_api.auth_scope import Principal
from agriinsight.analytics_api.errors import ApiProblem, CORRELATION_HEADER
from agriinsight.analytics_api.settings import AnalyticsSettings
from agriinsight.analytics_api.spring_scope_models import (
    CurrentUser,
    FarmItem,
    FarmPage,
    WarehouseItem,
    WarehousePage,
)


class SpringScopeClient:
    def __init__(
        self,
        settings: AnalyticsSettings,
        client: httpx.AsyncClient | None = None,
    ) -> None:
        self._settings = settings
        self._owned_client = client is None
        self._client = client or httpx.AsyncClient(
            base_url=settings.spring_base_url,
            follow_redirects=False,
            timeout=httpx.Timeout(
                connect=settings.connect_timeout_seconds,
                read=settings.read_timeout_seconds,
                write=settings.read_timeout_seconds,
                pool=settings.connect_timeout_seconds,
            ),
        )

    async def close(self) -> None:
        if self._owned_client:
            await self._client.aclose()

    async def current_user(self, bearer: str, correlation_id: str) -> CurrentUser:
        try:
            return CurrentUser.model_validate(
                await self._get_json(
                    "/api/v1/me",
                    bearer,
                    correlation_id,
                )
            )
        except ValidationError as error:
            raise _upstream_failure() from error

    async def farm_catalog(
        self, bearer: str, correlation_id: str
    ) -> list[FarmItem]:
        return await self._paged(
            "/api/v1/farms",
            FarmPage,
            bearer,
            correlation_id,
        )

    async def warehouse_catalog(
        self, bearer: str, correlation_id: str
    ) -> list[WarehouseItem]:
        return await self._paged(
            "/api/v1/warehouses",
            WarehousePage,
            bearer,
            correlation_id,
        )

    async def _paged(
        self,
        path: str,
        page_type: type[FarmPage] | type[WarehousePage],
        bearer: str,
        correlation_id: str,
    ) -> list[Any]:
        items: list[Any] = []
        offset = 0
        while True:
            try:
                page = page_type.model_validate(
                    await self._get_json(
                        path,
                        bearer,
                        correlation_id,
                        params={"limit": 100, "offset": offset, "active": "true"},
                    )
                )
            except ValidationError as error:
                raise _upstream_failure() from error
            items.extend(item for item in page.items if item.active)
            if not page.hasMore:
                return items
            offset += 100
            if offset > 10_000:
                raise _upstream_failure()

    async def _get_json(
        self,
        path: str,
        bearer: str,
        correlation_id: str,
        params: dict[str, Any] | None = None,
    ) -> Any:
        if not bearer.startswith("Bearer ") or len(bearer) > 8192:
            raise ApiProblem(401, "authentication_required", "Bearer authentication is required.")
        for attempt in range(self._settings.upstream_attempts):
            try:
                response = await self._request(
                    path,
                    bearer,
                    correlation_id,
                    params,
                )
            except httpx.TransportError:
                if attempt + 1 == self._settings.upstream_attempts:
                    raise _upstream_failure()
                await _retry_pause(attempt)
                continue
            if response[0] in {502, 503, 504} and (
                attempt + 1 < self._settings.upstream_attempts
            ):
                await _retry_pause(attempt)
                continue
            return _decode_response(*response)
        raise _upstream_failure()

    async def _request(
        self,
        path: str,
        bearer: str,
        correlation_id: str,
        params: dict[str, Any] | None,
    ) -> tuple[int, bytes]:
        async with self._client.stream(
            "GET",
            path,
            params=params,
            headers={
                "Authorization": bearer,
                "Accept": "application/json",
                CORRELATION_HEADER: correlation_id,
            },
        ) as response:
            if 300 <= response.status_code < 400:
                raise _upstream_failure()
            if response.status_code == 200 and not response.headers.get(
                "Content-Type", ""
            ).lower().startswith("application/json"):
                raise _upstream_failure()
            content_length = response.headers.get("Content-Length")
            if content_length:
                try:
                    parsed_length = int(content_length)
                except ValueError as error:
                    raise _upstream_failure() from error
                if (
                    parsed_length < 0
                    or parsed_length > self._settings.max_upstream_bytes
                ):
                    raise _upstream_failure()
            body = bytearray()
            async for chunk in response.aiter_bytes():
                body.extend(chunk)
                if len(body) > self._settings.max_upstream_bytes:
                    raise _upstream_failure()
            return response.status_code, bytes(body)


def principal_from(user: CurrentUser, demo_tenant_id: UUID) -> Principal:
    if user.tenantId != demo_tenant_id:
        raise ApiProblem(
            403,
            "demo_tenant_required",
            "Analytics is enabled only for the configured demo tenant.",
        )
    return Principal(
        permissions=frozenset(user.permissions),
        roles=frozenset(user.roles),
        tenant_id=user.tenantId,
    )


def _decode_response(status_code: int, body: bytes) -> Any:
    if status_code == 401:
        raise ApiProblem(401, "authentication_required", "Bearer authentication is required.")
    if status_code == 403:
        raise ApiProblem(403, "spring_scope_denied", "Spring denied the requested scope.")
    if status_code != 200:
        raise _upstream_failure()
    try:
        return json.loads(body)
    except (UnicodeError, json.JSONDecodeError) as error:
        raise _upstream_failure() from error


def _upstream_failure() -> ApiProblem:
    return ApiProblem(
        502,
        "spring_upstream_failure",
        "The operational authorization service returned an unusable response.",
    )


async def _retry_pause(attempt: int) -> None:
    await asyncio.sleep(0.02 * (attempt + 1) + random.uniform(0.0, 0.01))
