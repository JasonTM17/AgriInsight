from __future__ import annotations

from dataclasses import dataclass
from enum import StrEnum
from uuid import UUID

from agriinsight.analytics_api.errors import ApiProblem

TENANT_WIDE_ROLES = frozenset({"TENANT_ADMIN", "EXECUTIVE", "DATA_ANALYST"})


class AnalyticsArea(StrEnum):
    CATALOG = "catalog"
    COSTS = "costs"
    CROP_HEALTH = "crop_health"
    DATA_QUALITY = "data_quality"
    FARMS = "farms"
    INVENTORY = "inventory"
    OVERVIEW = "overview"


@dataclass(frozen=True, slots=True)
class Principal:
    permissions: frozenset[str]
    roles: frozenset[str]
    tenant_id: UUID


@dataclass(frozen=True, slots=True)
class ScopePlan:
    farm_tenant_wide: bool = False
    needs_farms: bool = False
    needs_warehouses: bool = False
    warehouse_tenant_wide: bool = False


@dataclass(frozen=True, slots=True)
class AuthorizedScope:
    farm_codes: frozenset[str]
    farm_tenant_wide: bool
    tenant_id: UUID
    warehouse_codes: frozenset[str]
    warehouse_tenant_wide: bool

    @property
    def tenant_wide(self) -> bool:
        return self.farm_tenant_wide or self.warehouse_tenant_wide


def plan_scope(principal: Principal, area: AnalyticsArea) -> ScopePlan:
    roles = principal.roles
    permissions = principal.permissions
    tenant_roles = roles & TENANT_WIDE_ROLES
    if area is AnalyticsArea.DATA_QUALITY:
        if roles & {"TENANT_ADMIN", "DATA_ANALYST"}:
            return ScopePlan(farm_tenant_wide=True)
        raise _denied()
    if area is AnalyticsArea.INVENTORY:
        if "INVENTORY_READ" not in permissions:
            raise _denied()
        if tenant_roles:
            return ScopePlan(warehouse_tenant_wide=True, needs_warehouses=True)
        if "INVENTORY_MANAGER" in roles:
            return ScopePlan(needs_warehouses=True)
        raise _denied()
    if area in {AnalyticsArea.FARMS, AnalyticsArea.CROP_HEALTH}:
        if "FARM_READ" not in permissions:
            raise _denied()
        if tenant_roles:
            return ScopePlan(farm_tenant_wide=True, needs_farms=True)
        if "FARM_MANAGER" in roles:
            return ScopePlan(needs_farms=True)
        raise _denied()
    if area in {AnalyticsArea.OVERVIEW, AnalyticsArea.COSTS}:
        if not {"FARM_READ", "COST_READ"}.issubset(permissions):
            raise _denied()
        if tenant_roles:
            return ScopePlan(farm_tenant_wide=True, needs_farms=True)
        if "FARM_MANAGER" in roles:
            return ScopePlan(needs_farms=True)
        raise _denied()
    if area is AnalyticsArea.CATALOG:
        farm_grant = "FARM_READ" in permissions and bool(
            tenant_roles or "FARM_MANAGER" in roles
        )
        inventory_grant = "INVENTORY_READ" in permissions and bool(
            tenant_roles or "INVENTORY_MANAGER" in roles
        )
        if not farm_grant and not inventory_grant:
            raise _denied()
        return ScopePlan(
            farm_tenant_wide=farm_grant and bool(tenant_roles),
            needs_farms=farm_grant,
            needs_warehouses=inventory_grant,
            warehouse_tenant_wide=inventory_grant and bool(tenant_roles),
        )
    raise _denied()


def _denied() -> ApiProblem:
    return ApiProblem(
        403,
        "analytics_forbidden",
        "The authenticated principal cannot access this analytics area.",
    )
