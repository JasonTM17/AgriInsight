from __future__ import annotations

import json
from pathlib import Path
from uuid import UUID, uuid5

import pytest
from fastapi.testclient import TestClient

from agriinsight.analytics_api.app import create_app
from agriinsight.analytics_api.assistant_settings import AssistantSettings
from agriinsight.analytics_api.settings import AnalyticsSettings
from agriinsight.analytics_api.spring_scope_client import (
    CurrentUser,
    FarmItem,
    WarehouseItem,
)
from agriinsight.demo_tenant_contract import (
    load_demo_contract,
    load_demo_snapshot,
)
from agriinsight.demo_tenant_reconciliation import (
    expected_operational_state,
    reconcile_catalog,
)

TENANT_ID = UUID("20000000-0000-4000-8000-000000000001")
NAMESPACE = UUID("20000000-0000-4000-8000-00000000a912")
ALL_PERMISSIONS = {
    "COST_READ",
    "FARM_READ",
    "INVENTORY_READ",
}


class FakeSpringClient:
    def __init__(
        self,
        *,
        tenant_id: UUID = TENANT_ID,
        roles: set[str] | None = None,
        permissions: set[str] | None = None,
        farms: list[FarmItem],
        warehouses: list[WarehouseItem],
    ) -> None:
        self.calls = {"me": 0, "farms": 0, "warehouses": 0}
        self.closed = False
        self.farms = farms
        self.warehouses = warehouses
        self.user = CurrentUser(
            assurance="oidc",
            displayName="Analytics Test",
            email="analytics@demo.invalid",
            permissions=sorted(permissions or ALL_PERMISSIONS),
            profileId=uuid5(NAMESPACE, "profile"),
            roles=sorted(roles or {"TENANT_ADMIN"}),
            tenantCode="AGRIINSIGHT_DEMO",
            tenantId=tenant_id,
        )

    async def current_user(self, bearer: str, correlation: str) -> CurrentUser:
        assert bearer == "Bearer test-token"
        assert correlation
        self.calls["me"] += 1
        return self.user

    async def farm_catalog(self, _bearer: str, _correlation: str):
        self.calls["farms"] += 1
        return self.farms

    async def warehouse_catalog(self, _bearer: str, _correlation: str):
        self.calls["warehouses"] += 1
        return self.warehouses

    async def close(self) -> None:
        self.closed = True


@pytest.fixture
def api_factory(
    analytics_artifact_root: Path,
    tmp_path: Path,
):
    snapshot = load_demo_snapshot(analytics_artifact_root)
    contract = load_demo_contract(Path("deploy/demo/demo-tenant.json"))
    report = reconcile_catalog(
        contract,
        snapshot,
        expected_operational_state(contract, snapshot),
    )
    report_path = tmp_path / "reconciliation.json"
    report_path.write_text(json.dumps(report), encoding="utf-8")

    farm_rows = snapshot.csv["farms"]
    farms = [
        FarmItem(
            active=True,
            code=row.farm_code,
            displayName=row.farm_name,
            id=uuid5(NAMESPACE, f"farm:{row.farm_code}"),
            version=0,
        )
        for row in farm_rows.itertuples(index=False)
    ]
    warehouse_rows = snapshot.csv["warehouses"]
    warehouses = [
        WarehouseItem(
            active=True,
            code=row.warehouse_code,
            displayName=row.warehouse_name,
            id=uuid5(NAMESPACE, f"warehouse:{row.warehouse_code}"),
            locationText=None,
            version=0,
        )
        for row in warehouse_rows.itertuples(index=False)
    ]

    def factory(
        *,
        tenant_id: UUID = TENANT_ID,
        roles: set[str] | None = None,
        permissions: set[str] | None = None,
        selected_farms: list[FarmItem] | None = None,
        selected_warehouses: list[WarehouseItem] | None = None,
        assistant_settings: AssistantSettings | None = None,
        assistant_service=None,
    ):
        spring = FakeSpringClient(
            tenant_id=tenant_id,
            roles=roles,
            permissions=permissions,
            farms=farms if selected_farms is None else selected_farms,
            warehouses=(
                warehouses
                if selected_warehouses is None
                else selected_warehouses
            ),
        )
        settings = AnalyticsSettings(
            artifact_root=analytics_artifact_root,
            demo_tenant_id=TENANT_ID,
            reconciliation_report=report_path,
            spring_base_url="http://spring.test",
            assistant=assistant_settings or AssistantSettings(),
        )
        app = create_app(
            settings,
            spring_client=spring,
            assistant_service=assistant_service,
        )
        client = TestClient(app, raise_server_exceptions=False)
        return app, client, spring

    return factory
