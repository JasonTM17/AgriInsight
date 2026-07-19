from __future__ import annotations

from typing import Mapping

import pandas as pd
import pytest

from agriinsight.cost_dashboard import (
    prepare_operating_dashboard,
    prepare_procurement_dashboard,
)
from agriinsight.cost_report_contract import CostReportDomains, CostReportRequest


def _request(
    gold: Mapping[str, pd.DataFrame], raw: Mapping[str, object]
) -> CostReportRequest:
    return CostReportRequest.from_mapping(raw, CostReportDomains.from_gold(gold))


def test_operating_dashboard_uses_filtered_detail_and_season_context(
    report_sources,
) -> None:
    gold, _ = report_sources
    row = gold["cost_activity_detail"].iloc[0]
    request = _request(
        gold,
        {
            "scope": "operating",
            "farm": row["farm_code"],
            "crop": row["crop_code"],
            "season": row["season_code"],
            "activity": row["activity_type"],
            "month_from": row["month"],
            "month_to": row["month"],
            "top_n": 5,
        },
    )

    view = prepare_operating_dashboard(gold, request)

    assert not view.detail.empty
    assert set(view.detail["farm_code"]) == {row["farm_code"]}
    assert set(view.detail["season_code"]) == {row["season_code"]}
    assert set(view.detail["activity_type"]) == {row["activity_type"]}
    assert set(view.detail["month"]) == {row["month"]}
    assert view.operating_total_cost_vnd == pytest.approx(
        view.detail["operating_total_cost_vnd"].sum()
    )
    assert view.budget_variance_vnd == pytest.approx(
        view.season_context["budget_variance_vnd"].sum()
    )
    assert view.operating_cost_per_ha_vnd == pytest.approx(
        view.season_context["operating_total_cost_vnd"].sum()
        / view.season_context["area_ha"].sum()
    )
    assert view.context_is_broader


def test_operating_dashboard_returns_stable_empty_view(report_sources) -> None:
    gold, _ = report_sources
    empty_gold = dict(gold)
    empty_gold["cost_activity_detail"] = gold["cost_activity_detail"].iloc[0:0]
    empty_gold["cost_season"] = gold["cost_season"].iloc[0:0]
    request = CostReportRequest(scope="operating")

    view = prepare_operating_dashboard(empty_gold, request)

    assert view.operating_total_cost_vnd == 0
    assert view.budget_variance_vnd is None
    assert view.operating_cost_per_ha_vnd is None
    assert view.operating_cost_per_kg_vnd is None
    assert view.activity_drivers.empty
    assert view.detail.empty


def test_procurement_dashboard_keeps_procurement_separate(report_sources) -> None:
    gold, _ = report_sources
    row = gold["procurement_detail"].iloc[0]
    request = _request(
        gold,
        {
            "scope": "procurement",
            "farm": row["farm_code"],
            "supplier": row["supplier_code"],
            "month_from": row["month"],
            "month_to": row["month"],
            "top_n": 3,
        },
    )

    view = prepare_procurement_dashboard(gold, request)

    assert not view.detail.empty
    assert set(view.detail["supplier_code"]) == {row["supplier_code"]}
    assert view.transaction_count == len(view.detail)
    assert view.procurement_spend_vnd == pytest.approx(
        view.detail["procurement_spend_vnd"].sum()
    )
    assert len(view.material_drivers) <= 3
    assert "warehouse_name" in view.material_drivers
    assert {
        "farm_code",
        "supplier_code",
        "warehouse_code",
        "material_code",
    }.issubset(view.material_drivers.columns)
    assert "operating_total_cost_vnd" not in view.material_drivers


def test_procurement_drivers_do_not_merge_distinct_codes_with_same_names() -> None:
    detail = pd.DataFrame(
        [
            {
                "transaction_id": "TX-001",
                "transaction_date": "2026-07-01",
                "month": "2026-07",
                "farm_code": "FARM-001",
                "farm_name": "Nông trại A",
                "supplier_code": "SUP-001",
                "supplier_name": "Nhà cung cấp trùng tên",
                "warehouse_code": "WH-001",
                "warehouse_name": "Kho trùng tên",
                "material_code": "MAT-001",
                "material_name": "Vật tư trùng tên",
                "base_unit": "kg",
                "procurement_quantity_base_unit": 10.0,
                "procurement_spend_vnd": 100.0,
            },
            {
                "transaction_id": "TX-002",
                "transaction_date": "2026-07-02",
                "month": "2026-07",
                "farm_code": "FARM-001",
                "farm_name": "Nông trại A",
                "supplier_code": "SUP-002",
                "supplier_name": "Nhà cung cấp trùng tên",
                "warehouse_code": "WH-002",
                "warehouse_name": "Kho trùng tên",
                "material_code": "MAT-002",
                "material_name": "Vật tư trùng tên",
                "base_unit": "kg",
                "procurement_quantity_base_unit": 20.0,
                "procurement_spend_vnd": 200.0,
            },
        ]
    )

    view = prepare_procurement_dashboard(
        {"procurement_detail": detail},
        CostReportRequest(scope="procurement"),
    )

    assert len(view.material_drivers) == 2
    assert set(view.material_drivers["supplier_code"]) == {"SUP-001", "SUP-002"}
    assert view.material_drivers["procurement_spend_vnd"].sum() == 300.0
