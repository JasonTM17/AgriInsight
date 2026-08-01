from __future__ import annotations

from uuid import UUID

import pytest

from agriinsight.analytics_api.auth_scope import (
    AnalyticsArea,
    Principal,
    plan_scope,
)
from agriinsight.analytics_api.errors import ApiProblem

TENANT = UUID("20000000-0000-4000-8000-000000000001")


def _principal(role: str, *permissions: str) -> Principal:
    return Principal(
        permissions=frozenset(permissions),
        roles=frozenset({role}),
        tenant_id=TENANT,
    )


@pytest.mark.parametrize(
    ("role", "area", "permissions", "tenant_wide"),
    [
        ("TENANT_ADMIN", AnalyticsArea.DATA_QUALITY, (), True),
        (
            "EXECUTIVE",
            AnalyticsArea.OVERVIEW,
            ("FARM_READ", "COST_READ"),
            True,
        ),
        ("TENANT_ADMIN", AnalyticsArea.FARMS, ("FARM_READ",), True),
        ("FARM_MANAGER", AnalyticsArea.FARMS, ("FARM_READ",), False),
        (
            "INVENTORY_MANAGER",
            AnalyticsArea.INVENTORY,
            ("INVENTORY_READ",),
            False,
        ),
        (
            "DATA_ANALYST",
            AnalyticsArea.INVENTORY,
            ("INVENTORY_READ",),
            True,
        ),
    ],
)
def test_authorized_matrix(
    role,
    area,
    permissions,
    tenant_wide,
) -> None:
    plan = plan_scope(_principal(role, *permissions), area)

    assert (plan.farm_tenant_wide or plan.warehouse_tenant_wide) is tenant_wide


@pytest.mark.parametrize("role", ["FIELD_WORKER", "SUPPLIER"])
@pytest.mark.parametrize("area", list(AnalyticsArea))
def test_worker_and_supplier_have_no_analytics_grant(role, area) -> None:
    with pytest.raises(ApiProblem) as captured:
        plan_scope(
            _principal(
                role,
                "FARM_READ",
                "COST_READ",
                "INVENTORY_READ",
            ),
            area,
        )

    assert captured.value.status_code == 403


def test_multi_role_union_is_domain_specific() -> None:
    principal = Principal(
        permissions=frozenset({"FARM_READ", "INVENTORY_READ"}),
        roles=frozenset({"DATA_ANALYST", "INVENTORY_MANAGER"}),
        tenant_id=TENANT,
    )

    inventory = plan_scope(principal, AnalyticsArea.INVENTORY)

    assert inventory.warehouse_tenant_wide is True
    with pytest.raises(ApiProblem):
        plan_scope(principal, AnalyticsArea.COSTS)
