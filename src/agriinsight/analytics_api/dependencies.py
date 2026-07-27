from __future__ import annotations

from dataclasses import dataclass
from uuid import UUID

from fastapi import Depends, Request
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer

from agriinsight.analytics_api.auth_scope import (
    AnalyticsArea,
    AuthorizedScope,
    plan_scope,
)
from agriinsight.analytics_api.assistant_models import EvidenceSource
from agriinsight.analytics_api.errors import ApiProblem, correlation_id
from agriinsight.analytics_api.spring_scope_client import (
    CurrentUser,
    FarmItem,
    SpringScopeClient,
    WarehouseItem,
    principal_from,
)

_bearer = HTTPBearer(auto_error=False)


class RequestScopeResolver:
    def __init__(
        self,
        bearer: str,
        correlation: str,
        demo_tenant_id: UUID,
        spring: SpringScopeClient,
    ) -> None:
        self._bearer = bearer
        self._correlation = correlation
        self._demo_tenant_id = demo_tenant_id
        self._spring = spring
        self._farms: list[FarmItem] | None = None
        self._user: CurrentUser | None = None
        self._warehouses: list[WarehouseItem] | None = None

    async def authorize(self, area: AnalyticsArea) -> AuthorizedScope:
        user = await self._current_user()
        principal = principal_from(user, self._demo_tenant_id)
        plan = plan_scope(principal, area)
        farms = await self._farm_catalog() if plan.needs_farms else []
        warehouses = await self._warehouse_catalog() if plan.needs_warehouses else []
        _require_unique_active_catalog(farms, "farm")
        _require_unique_active_catalog(warehouses, "warehouse")
        farm_codes = (
            frozenset(item.code for item in farms if item.active)
            if plan.needs_farms
            else frozenset()
        )
        warehouse_codes = (
            frozenset(item.code for item in warehouses if item.active)
            if plan.needs_warehouses
            else frozenset()
        )
        if plan.needs_farms and not plan.farm_tenant_wide and not farm_codes:
            raise _empty_scope()
        if (
            plan.needs_warehouses
            and not plan.warehouse_tenant_wide
            and not warehouse_codes
        ):
            raise _empty_scope()
        return AuthorizedScope(
            farm_codes=farm_codes,
            farm_tenant_wide=plan.farm_tenant_wide,
            tenant_id=principal.tenant_id,
            warehouse_codes=warehouse_codes,
            warehouse_tenant_wide=plan.warehouse_tenant_wide,
        )

    async def _current_user(self) -> CurrentUser:
        if self._user is None:
            self._user = await self._spring.current_user(
                self._bearer, self._correlation
            )
        return self._user

    async def _farm_catalog(self) -> list[FarmItem]:
        if self._farms is None:
            self._farms = await self._spring.farm_catalog(
                self._bearer, self._correlation
            )
        return self._farms

    async def _warehouse_catalog(self) -> list[WarehouseItem]:
        if self._warehouses is None:
            self._warehouses = await self._spring.warehouse_catalog(
                self._bearer, self._correlation
            )
        return self._warehouses

    async def farm_items(self) -> list[FarmItem]:
        return await self._farm_catalog()

    async def warehouse_items(self) -> list[WarehouseItem]:
        return await self._warehouse_catalog()

    async def authorize_assistant(self) -> AssistantAuthorization:
        user = await self._current_user()
        principal = principal_from(user, self._demo_tenant_id)
        roles = principal.roles
        permissions = principal.permissions
        if "SUPPLIER" in roles:
            raise _assistant_denied()
        tenant_wide = bool(roles & {"TENANT_ADMIN", "EXECUTIVE", "DATA_ANALYST"})
        farm_access = "FARM_READ" in permissions and bool(
            tenant_wide or "FARM_MANAGER" in roles
        )
        cost_access = farm_access and "COST_READ" in permissions
        inventory_access = "INVENTORY_READ" in permissions and bool(
            tenant_wide or "INVENTORY_MANAGER" in roles
        )
        if not farm_access and not inventory_access:
            raise _assistant_denied()

        farms = await self._farm_catalog() if farm_access else []
        warehouses = await self._warehouse_catalog() if inventory_access else []
        _require_unique_active_catalog(farms, "farm")
        _require_unique_active_catalog(warehouses, "warehouse")
        farm_codes = frozenset(item.code for item in farms if item.active)
        warehouse_codes = frozenset(item.code for item in warehouses if item.active)
        if farm_access and not tenant_wide and not farm_codes:
            raise _empty_scope()
        if inventory_access and not tenant_wide and not warehouse_codes:
            raise _empty_scope()

        sources: set[EvidenceSource] = set()
        if farm_access:
            sources.update({"farm-performance", "crop-health"})
        if cost_access:
            sources.update({"cost", "overview"})
        if inventory_access:
            sources.add("inventory")
        return AssistantAuthorization(
            scope=AuthorizedScope(
                farm_codes=farm_codes,
                farm_tenant_wide=farm_access and tenant_wide,
                tenant_id=principal.tenant_id,
                warehouse_codes=warehouse_codes,
                warehouse_tenant_wide=inventory_access and tenant_wide,
            ),
            sources=frozenset(sources),
        )


@dataclass(frozen=True, slots=True)
class AssistantAuthorization:
    scope: AuthorizedScope
    sources: frozenset[EvidenceSource]


async def request_scope_resolver(
    request: Request,
    credentials: HTTPAuthorizationCredentials | None = Depends(_bearer),
) -> RequestScopeResolver:
    if credentials is None or credentials.scheme.lower() != "bearer":
        raise ApiProblem(
            401,
            "authentication_required",
            "Bearer authentication is required.",
        )
    return RequestScopeResolver(
        bearer=f"Bearer {credentials.credentials}",
        correlation=correlation_id(request),
        demo_tenant_id=request.app.state.settings.demo_tenant_id,
        spring=request.app.state.spring_client,
    )


async def assistant_authorization(
    resolver: RequestScopeResolver = Depends(request_scope_resolver),
) -> AssistantAuthorization:
    return await resolver.authorize_assistant()


def _empty_scope() -> ApiProblem:
    return ApiProblem(
        403,
        "analytics_scope_empty",
        "No active operational scope is assigned for this analytics area.",
    )


def _assistant_denied() -> ApiProblem:
    return ApiProblem(
        403,
        "analytics_forbidden",
        "The authenticated principal cannot access this analytics area.",
    )


def _require_unique_active_catalog(
    items: list[FarmItem] | list[WarehouseItem],
    resource: str,
) -> None:
    active = [item for item in items if item.active]
    codes = [item.code for item in active]
    identifiers = [str(item.id) for item in active]
    if len(set(codes)) != len(codes) or len(set(identifiers)) != len(identifiers):
        raise ApiProblem(
            502,
            "spring_upstream_failure",
            f"The operational {resource} catalog is ambiguous.",
        )
