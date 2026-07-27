from __future__ import annotations

from datetime import datetime, timezone
from types import MappingProxyType
from uuid import UUID

import pandas as pd

from agriinsight.analytics_api.assistant_corpus import build_evidence_corpus
from agriinsight.analytics_api.auth_scope import AuthorizedScope
from agriinsight.analytics_snapshot import ArtifactSnapshot


TENANT_ID = UUID("20000000-0000-4000-8000-000000000001")


def _snapshot() -> ArtifactSnapshot:
    return ArtifactSnapshot(
        csv=MappingProxyType(
            {
                "farm_performance": pd.DataFrame(
                    [
                        {
                            "farm_code": "FARM-01",
                            "farm_name": "Trang trại Một",
                            "cultivated_area_ha": 10,
                            "harvest_quantity_kg": 20_000,
                            "total_revenue_vnd": 500_000_000,
                            "total_cost_vnd": 300_000_000,
                            "profit_vnd": 200_000_000,
                            "yield_kg_per_ha": 2_000,
                            "profit_margin_pct": 40,
                        },
                        {
                            "farm_code": "FARM-02",
                            "farm_name": "Trang trại <b>Hai</b>",
                            "cultivated_area_ha": 20,
                            "harvest_quantity_kg": 50_000,
                            "total_revenue_vnd": 900_000_000,
                            "total_cost_vnd": 600_000_000,
                            "profit_vnd": 300_000_000,
                            "yield_kg_per_ha": 2_500,
                            "profit_margin_pct": 33.333,
                        },
                    ]
                ),
                "cost_farm": pd.DataFrame(
                    [
                        {
                            "farm_code": "FARM-01",
                            "farm_name": "Trang trại Một",
                            "operating_total_cost_vnd": 300_000_000,
                            "budget_operating_cost_vnd": 280_000_000,
                            "budget_variance_vnd": 20_000_000,
                            "operating_profit_vnd": 200_000_000,
                            "operating_profit_margin_pct": 40,
                            "operating_cost_per_ha_vnd": 30_000_000,
                        }
                    ]
                ),
                "field_health_status": pd.DataFrame(),
                "inventory_status": pd.DataFrame(
                    [
                        {
                            "farm_code": "FARM-01",
                            "warehouse_code": "WH-01",
                            "warehouse_name": "Kho Một",
                            "material_code": "MAT-01",
                            "material_name": "Phân hữu cơ",
                            "stock_quantity": 120,
                            "base_unit": "kg",
                            "inventory_value_vnd": 12_000_000,
                            "days_of_supply": 14,
                            "stock_status": "LOW",
                            "recommended_order_quantity": 80,
                        },
                        {
                            "farm_code": "FARM-02",
                            "warehouse_code": "WH-02",
                            "warehouse_name": "Kho Hai",
                            "material_code": "MAT-02",
                            "material_name": "Bí mật",
                            "stock_quantity": 999,
                            "base_unit": "kg",
                            "inventory_value_vnd": 999_000_000,
                            "days_of_supply": 99,
                            "stock_status": "OK",
                            "recommended_order_quantity": 0,
                        },
                    ]
                ),
                "executive_summary": pd.DataFrame(
                    [
                        {
                            "total_revenue_vnd": 1_400_000_000,
                            "total_cost_vnd": 900_000_000,
                            "profit_vnd": 500_000_000,
                            "profit_margin_pct": 35.714,
                            "harvest_quantity_kg": 70_000,
                            "cultivated_area_ha": 30,
                            "risk_alerts": 3,
                        }
                    ]
                ),
            }
        ),
        generated_at=datetime(2026, 7, 27, tzinfo=timezone.utc),
        json=MappingProxyType({}),
        manifest=MappingProxyType({"as_of_date": "2026-07-27"}),
        manifest_fingerprint="manifest",
        source_fingerprint="source",
    )


def _scope(*, tenant_wide: bool = False) -> AuthorizedScope:
    return AuthorizedScope(
        farm_codes=frozenset({"FARM-01"}),
        farm_tenant_wide=tenant_wide,
        tenant_id=TENANT_ID,
        warehouse_codes=frozenset({"WH-01"}),
        warehouse_tenant_wide=tenant_wide,
    )


def test_corpus_is_filtered_before_chunks_are_created() -> None:
    chunks = build_evidence_corpus(
        _snapshot(),
        _scope(),
        sources=frozenset(
            {"farm-performance", "cost", "inventory", "overview"}
        ),
    )

    identifiers = [chunk.evidence_id for chunk in chunks]
    assert identifiers == [
        "cost:farm-01",
        "farm-performance:farm-01",
        "inventory:wh-01:mat-01",
    ]
    combined = " ".join(chunk.content for chunk in chunks)
    assert "FARM-02" not in combined
    assert "MAT-02" not in combined
    assert "<" not in combined


def test_tenant_wide_scope_receives_global_and_all_resource_chunks() -> None:
    chunks = build_evidence_corpus(
        _snapshot(),
        _scope(tenant_wide=True),
        sources=frozenset({"farm-performance", "inventory", "overview"}),
    )

    assert [chunk.evidence_id for chunk in chunks] == [
        "farm-performance:farm-01",
        "farm-performance:farm-02",
        "inventory:wh-01:mat-01",
        "inventory:wh-02:mat-02",
        "overview:executive-summary",
    ]
    assert all("<" not in chunk.title for chunk in chunks)
