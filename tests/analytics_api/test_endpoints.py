from __future__ import annotations

import json
from dataclasses import replace
from datetime import date, datetime, timedelta, timezone
from types import MappingProxyType
from uuid import UUID

import pandas as pd
import pytest

from agriinsight.analytics_api import response_bounds
from agriinsight.analytics_api.domain_read_models import _inventory_abc
from agriinsight.metrics_inventory_forecast_contract import (
    INVENTORY_STATUS_FORECAST_COLUMNS,
)

HEADERS = {
    "Authorization": "Bearer test-token",
    "X-Correlation-Id": "phase2-test-001",
}
FORECAST_KEYS = {
    "asOfDate",
    "modelVersion",
    "coverageStatus",
    "historyStartDate",
    "historyEndDate",
    "historyDays",
    "nonzeroDemandDays",
    "horizonDays",
    "forecastQuantity",
    "lowerQuantity",
    "upperQuantity",
    "backtestWindows",
    "backtestMae",
    "backtestWapePct",
    "forecastDaysOfSupply",
    "forecastSuggestedOrderQuantity",
}
YIELD_FORECAST_KEYS = {
    "asOfDate",
    "farmCode",
    "fieldCode",
    "seasonCode",
    "cropCode",
    "modelVersion",
    "forecastStatus",
    "forecastOriginDate",
    "expectedHarvestDate",
    "seasonAreaHa",
    "targetYieldKg",
    "historyStartAt",
    "historyEndAt",
    "historySeasons",
    "backtestOrigins",
    "backtestSeasons",
    "forecastYieldKgPerHa",
    "observedMinYieldKgPerHa",
    "observedMaxYieldKgPerHa",
    "forecastQuantityKg",
    "observedMinQuantityKg",
    "observedMaxQuantityKg",
    "backtestMaeKgPerHa",
    "backtestWapePct",
}


def test_all_read_endpoints_return_typed_bounded_envelopes(api_factory) -> None:
    _app, client, spring = api_factory()
    paths = [
        "/internal/v1/catalog",
        "/internal/v1/overview",
        "/internal/v1/farms?limit=2",
        "/internal/v1/inventory?limit=2",
        "/internal/v1/crop-health?limit=2",
        "/internal/v1/data-quality",
        "/internal/v1/costs",
    ]

    with client:
        ready = client.get("/health/ready")
        responses = [client.get(path, headers=HEADERS) for path in paths]

    assert ready.status_code == 200
    assert ready.json()["status"] == "ready"
    for response in responses:
        assert response.status_code == 200, response.text
        assert response.headers["X-Correlation-Id"] == "phase2-test-001"
        assert response.headers["Cache-Control"] == "no-store"
        body = response.json()
        assert set(body) == {"freshness", "lineage", "payload", "scope"}
        assert body["lineage"]["contractVersion"] == "1.0.0"
        assert body["scope"]["tenantId"].endswith("0001")
    assert responses[2].json()["payload"]["page"]["limit"] == 2
    assert len(responses[2].json()["payload"]["items"]) <= 2
    inventory_payload = responses[3].json()["payload"]
    inventory_items = inventory_payload["items"]
    assert len(inventory_items) <= 2
    assert all(
        set(item["forecast"]) == FORECAST_KEYS for item in inventory_items
    )
    assert all(
        item["forecast"]["coverageStatus"]
        in {"ready", "noDemand", "insufficientHistory", "unavailable"}
        for item in inventory_items
    )
    assert set(inventory_payload["forecastHealth"]) == {
        "ready",
        "noDemand",
        "insufficientHistory",
        "unavailable",
        "total",
    }
    crop_payload = responses[4].json()["payload"]
    assert len(crop_payload["fields"]) <= 2
    for field in crop_payload["fields"]:
        last_reading = datetime.fromisoformat(
            field["lastReadingAt"].replace("Z", "+00:00")
        )
        assert last_reading.utcoffset() is not None
    for incident in crop_payload["pestIncidentsWeekly"]:
        assert date.fromisoformat(incident["week"])
    assert spring.closed is True


def test_inventory_forecast_is_nested_and_preserves_legacy_policy(
    api_factory,
) -> None:
    app, client, _spring = api_factory()
    status = app.state.snapshot_cache.current().csv["inventory_status"]
    source = status.loc[
        status["forecast_coverage_status"] != "unavailable"
    ].iloc[0]

    with client:
        response = client.get(
            "/internal/v1/inventory",
            params={"warehouse_code": source["warehouse_code"]},
            headers=HEADERS,
        )

    assert response.status_code == 200, response.text
    item = next(
        item
        for item in response.json()["payload"]["items"]
        if item["materialCode"] == source["material_code"]
    )
    forecast = item["forecast"]
    assert set(item).isdisjoint(FORECAST_KEYS)
    assert {key for key in item if key.startswith("forecast")} == {"forecast"}
    assert item["predicted30dNeed"] == source["predicted_30d_need"]
    assert item["recommendedOrderQuantity"] == source["recommended_order_quantity"]
    assert forecast["coverageStatus"] == {
        "ready": "ready",
        "no_demand": "noDemand",
        "insufficient_history": "insufficientHistory",
    }[source["forecast_coverage_status"]]
    assert forecast["asOfDate"] == source["forecast_as_of_date"]
    assert forecast["modelVersion"] == source["forecast_model_version"]
    assert forecast["historyStartDate"] == source["forecast_history_start_date"]
    assert forecast["historyEndDate"] == source["forecast_history_end_date"]
    assert forecast["historyDays"] == source["forecast_history_days"]
    assert forecast["nonzeroDemandDays"] == source["forecast_nonzero_demand_days"]
    assert forecast["horizonDays"] == source["forecast_horizon_days"]
    assert forecast["forecastQuantity"] == pytest.approx(source["forecast_quantity"])
    assert forecast["lowerQuantity"] == pytest.approx(source["forecast_lower_quantity"])
    assert forecast["upperQuantity"] == pytest.approx(source["forecast_upper_quantity"])
    assert forecast["forecastDaysOfSupply"] == pytest.approx(
        source["forecast_days_of_supply"]
    )
    assert forecast["forecastSuggestedOrderQuantity"] == pytest.approx(
        source["forecast_suggested_order_quantity"]
    )
    if pd.isna(source["forecast_backtest_mae"]):
        assert forecast["backtestMae"] is None
        assert forecast["backtestWapePct"] is None
    else:
        assert forecast["backtestMae"] == pytest.approx(
            source["forecast_backtest_mae"]
        )
        assert forecast["backtestWapePct"] == pytest.approx(
            source["forecast_backtest_wape_pct"]
        )


def test_inventory_forecast_unavailable_evidence_is_null(api_factory) -> None:
    app, client, _spring = api_factory()
    status = app.state.snapshot_cache.current().csv["inventory_status"]
    source = status.iloc[0].copy()
    status.loc[source.name, "forecast_coverage_status"] = "unavailable"
    for column in INVENTORY_STATUS_FORECAST_COLUMNS:
        if column != "forecast_coverage_status":
            status.loc[source.name, column] = None

    with client:
        response = client.get(
            "/internal/v1/inventory",
            params={"warehouse_code": source["warehouse_code"]},
            headers=HEADERS,
        )

    assert response.status_code == 200, response.text
    item = next(
        item
        for item in response.json()["payload"]["items"]
        if item["materialCode"] == source["material_code"]
    )
    forecast = item["forecast"]
    assert forecast["coverageStatus"] == "unavailable"
    assert all(
        forecast[key] is None for key in FORECAST_KEYS - {"coverageStatus"}
    )


def test_inventory_forecast_health_is_limited_to_the_scoped_status_frame(
    api_factory,
) -> None:
    app, client, spring = api_factory()
    status = app.state.snapshot_cache.current().csv["inventory_status"]
    warehouse_code = spring.warehouses[0].code
    scoped = status.loc[status["warehouse_code"] == warehouse_code]
    assert 0 < len(scoped) < len(status)

    with client:
        response = client.get(
            "/internal/v1/inventory",
            params={"warehouse_code": warehouse_code},
            headers=HEADERS,
        )

    assert response.status_code == 200, response.text
    health = response.json()["payload"]["forecastHealth"]
    assert health == {
        "ready": int((scoped["forecast_coverage_status"] == "ready").sum()),
        "noDemand": int(
            (scoped["forecast_coverage_status"] == "no_demand").sum()
        ),
        "insufficientHistory": int(
            (scoped["forecast_coverage_status"] == "insufficient_history").sum()
        ),
        "unavailable": int(
            (scoped["forecast_coverage_status"] == "unavailable").sum()
        ),
        "total": len(scoped),
    }
    assert {
        item["warehouseCode"] for item in response.json()["payload"]["items"]
    } == {warehouse_code}


def test_inventory_abc_is_deterministically_capped_at_one_hundred() -> None:
    source = pd.DataFrame(
        {
            "material_code": [f"MAT-{number:03}" for number in range(101)],
            "material_name": [f"Material {number:03}" for number in range(101)],
            "category": ["fertilizer"] * 101,
            "inventory_value_vnd": [float(101 - number) for number in range(101)],
            "warehouse_code": ["WH-001"] * 101,
        }
    )

    abc = _inventory_abc(source)

    assert len(abc) == 100
    assert list(abc["material_code"][:2]) == ["MAT-000", "MAT-001"]
    assert (abc["value_share_pct"] <= 100).all()
    assert (abc["cumulative_value_share_pct"] <= 100).all()


def test_inventory_response_cap_is_sanitized(api_factory, monkeypatch) -> None:
    _app, client, _spring = api_factory()
    monkeypatch.setattr(response_bounds, "MAX_SERIALIZED_RESPONSE_BYTES", 1)

    with client:
        response = client.get("/internal/v1/inventory", headers=HEADERS)

    assert response.status_code == 503
    assert response.json()["error"]["code"] == "analytics_response_too_large"
    assert "artifacts" not in response.text
    assert "inventory_status" not in response.text


def test_yield_forecast_is_scoped_strict_and_deterministically_sorted(
    api_factory,
) -> None:
    app, client, spring = api_factory()
    selected_farm = spring.farms[0].code
    source = app.state.snapshot_cache.current().csv["yield_forecast"]
    scoped = source.loc[source["farm_code"] == selected_farm].sort_values(
        ["expected_harvest_date", "season_code"],
        kind="stable",
    )

    with client:
        response = client.get(
            "/internal/v1/yield-forecast",
            params={"farm_code": selected_farm, "limit": 1},
            headers=HEADERS,
        )

    assert response.status_code == 200, response.text
    body = response.json()
    payload = body["payload"]
    assert body["scope"]["appliedFilter"]["farmCode"] == selected_farm
    assert payload["page"] == {
        "hasMore": len(scoped) > 1,
        "limit": 1,
        "offset": 0,
        "total": len(scoped),
    }
    assert payload["forecastHealth"] == {
        "ready": int(scoped["forecast_status"].eq("ready").sum()),
        "insufficientHistory": int(
            scoped["forecast_status"].eq("insufficient_history").sum()
        ),
        "total": len(scoped),
    }
    assert len(payload["items"]) == min(1, len(scoped))
    assert all(set(item) == YIELD_FORECAST_KEYS for item in payload["items"])
    assert [item["seasonCode"] for item in payload["items"]] == list(
        scoped["season_code"].head(1)
    )
    assert all(item["farmCode"] == selected_farm for item in payload["items"])
    assert all(
        item["forecastStatus"] in {"ready", "insufficientHistory"}
        for item in payload["items"]
    )


def test_yield_forecast_canonical_filter_conflicts_and_page_bounds_fail_closed(
    api_factory,
) -> None:
    app, client, spring = api_factory()
    selected_farm = spring.farms[0].code
    seasons = app.state.snapshot_cache.current().csv["cost_season"]
    foreign_field = seasons.loc[
        seasons["farm_code"] != selected_farm,
        "field_code",
    ].iloc[0]

    with client:
        conflict = client.get(
            "/internal/v1/yield-forecast",
            params={"farm_code": selected_farm, "field_code": foreign_field},
            headers=HEADERS,
        )
        invalid_limit = client.get(
            "/internal/v1/yield-forecast?limit=101",
            headers=HEADERS,
        )
        invalid_offset = client.get(
            "/internal/v1/yield-forecast?offset=10001",
            headers=HEADERS,
        )
        lowercase_filter = client.get(
            "/internal/v1/yield-forecast?farm_code=farm-001",
            headers=HEADERS,
        )

    assert conflict.status_code == 422
    assert conflict.json()["error"]["code"] == "analytics_filter_conflict"
    assert invalid_limit.status_code == 422
    assert invalid_offset.status_code == 422
    assert lowercase_filter.status_code == 422


def test_yield_forecast_farm_manager_scope_excludes_other_farms(api_factory) -> None:
    app, client, spring = api_factory(
        roles={"FARM_MANAGER"},
        permissions={"FARM_READ"},
    )
    spring.farms = spring.farms[:1]
    allowed_farm = spring.farms[0].code
    source = app.state.snapshot_cache.current().csv["yield_forecast"]
    outside_farm = source.loc[
        source["farm_code"] != allowed_farm,
        "farm_code",
    ].iloc[0]

    with client:
        scoped = client.get("/internal/v1/yield-forecast", headers=HEADERS)
        forbidden = client.get(
            "/internal/v1/yield-forecast",
            params={"farm_code": outside_farm},
            headers=HEADERS,
        )

    assert scoped.status_code == 200, scoped.text
    assert scoped.json()["scope"]["farmCodes"] == [allowed_farm]
    assert {
        item["farmCode"] for item in scoped.json()["payload"]["items"]
    } <= {allowed_farm}
    assert forbidden.status_code == 403
    assert forbidden.json()["error"]["code"] == "farm_scope_forbidden"


@pytest.mark.parametrize("invalid_row", ["duplicate", "foreign_relationship"])
def test_yield_forecast_invalid_raw_rows_fail_before_public_shaping(
    api_factory,
    invalid_row,
) -> None:
    app, client, _spring = api_factory()
    snapshot = app.state.snapshot_cache.current()
    forecast = snapshot.csv["yield_forecast"]
    if invalid_row == "duplicate":
        mutated_forecast = pd.concat(
            [forecast, forecast.iloc[[0]]],
            ignore_index=True,
        )
    else:
        mutated_forecast = forecast.copy()
        mutated_forecast.loc[forecast.index[0], "farm_code"] = "FARM-FOREIGN"
    app.state.snapshot_cache._cached = replace(
        snapshot,
        csv=MappingProxyType(
            {
                **snapshot.csv,
                "yield_forecast": mutated_forecast,
            }
        ),
    )

    with client:
        response = client.get("/internal/v1/yield-forecast", headers=HEADERS)

    assert response.status_code == 503
    assert response.json()["error"]["code"] == "snapshot_contract_invalid"
    assert "yield_forecast" not in response.text


def test_yield_forecast_response_cap_is_sanitized(api_factory, monkeypatch) -> None:
    _app, client, _spring = api_factory()
    monkeypatch.setattr(response_bounds, "MAX_SERIALIZED_RESPONSE_BYTES", 1)

    with client:
        response = client.get("/internal/v1/yield-forecast", headers=HEADERS)

    assert response.status_code == 503
    assert response.json()["error"]["code"] == "analytics_response_too_large"
    assert "yield_forecast" not in response.text


@pytest.mark.parametrize(
    "path",
    [
        "/internal/v1/farms?farm_code=FARM-FOREIGN",
        "/internal/v1/yield-forecast?farm_code=FARM-FOREIGN",
        "/internal/v1/crop-health?farm_code=FARM-FOREIGN",
        "/internal/v1/inventory?warehouse_code=WH-FOREIGN",
    ],
)
def test_foreign_filters_fail_before_artifact_access(
    api_factory,
    path,
) -> None:
    app, client, _spring = api_factory()

    class ExplodingCache:
        def current(self):
            raise AssertionError("artifact must not be accessed")

    app.state.snapshot_cache = ExplodingCache()
    with client:
        response = client.get(path, headers=HEADERS)

    assert response.status_code == 403
    assert response.json()["error"]["code"].endswith("scope_forbidden")


def test_farm_manager_receives_partial_farm_scoped_analytics(api_factory) -> None:
    app, client, spring = api_factory(
        roles={"FARM_MANAGER"},
        permissions={"FARM_READ", "COST_READ"},
    )
    spring.farms = spring.farms[:1]
    allowed = spring.farms[0].code

    with client:
        overview = client.get("/internal/v1/overview", headers=HEADERS)
        costs = client.get("/internal/v1/costs", headers=HEADERS)

    assert overview.status_code == 200
    assert overview.json()["freshness"]["dataStatus"] == "partial"
    assert overview.json()["scope"]["farmCodes"] == [allowed]
    assert overview.json()["payload"]["monthlyTrend"] == []
    assert costs.json()["freshness"]["dataStatus"] == "partial"
    assert {item["farmCode"] for item in costs.json()["payload"]["farms"]} == {
        allowed
    }
    assert costs.json()["payload"]["capabilities"]["fileExportAvailable"] is False


def test_tenant_wide_scope_drift_fails_before_aggregate_response(
    api_factory,
) -> None:
    _app, client, spring = api_factory()
    spring.farms = spring.farms[:-1]

    with client:
        response = client.get("/internal/v1/overview", headers=HEADERS)

    assert response.status_code == 503
    assert response.json()["error"]["code"] == "operational_scope_drift"


def test_ambiguous_live_catalog_fails_closed(api_factory) -> None:
    _app, client, spring = api_factory()
    spring.farms.append(
        spring.farms[0].model_copy(update={"id": UUID("90000000-0000-4000-8000-000000000001")})
    )

    with client:
        response = client.get("/internal/v1/overview", headers=HEADERS)

    assert response.status_code == 502
    assert response.json()["error"]["code"] == "spring_upstream_failure"


def test_stale_reconciliation_report_fails_readiness(api_factory) -> None:
    app, client, _spring = api_factory()
    report_path = app.state.settings.reconciliation_report
    report = json.loads(report_path.read_text(encoding="utf-8"))
    report["generatedAt"] = (
        datetime.now(timezone.utc) - timedelta(days=2)
    ).isoformat()
    report_path.write_text(json.dumps(report), encoding="utf-8")

    with client:
        response = client.get("/health/ready")

    assert response.status_code == 503
    assert response.json()["error"]["code"] == "reconciliation_required"


def test_farm_filter_also_scopes_crop_profitability(api_factory) -> None:
    app, client, spring = api_factory()
    selected = spring.farms[0].code
    snapshot = app.state.snapshot_cache.current()
    expected_crops = set(
        snapshot.csv["cost_season"]
        .loc[
            snapshot.csv["cost_season"]["farm_code"] == selected,
            "crop_code",
        ]
        .astype(str)
    )

    with client:
        response = client.get(
            f"/internal/v1/farms?farm_code={selected}",
            headers=HEADERS,
        )

    assert response.status_code == 200
    payload = response.json()["payload"]
    assert {item["farmCode"] for item in payload["items"]} == {selected}
    assert {item["cropCode"] for item in payload["cropProfitability"]} == expected_crops


def test_crop_health_field_filter_is_exact_and_records_scope(api_factory) -> None:
    app, client, _spring = api_factory()
    fields = app.state.snapshot_cache.current().csv["field_health_status"]
    selected = fields.iloc[0]

    with client:
        response = client.get(
            "/internal/v1/crop-health",
            params={
                "farm_code": selected["farm_code"],
                "field_code": selected["field_code"],
            },
            headers=HEADERS,
        )

    assert response.status_code == 200, response.text
    body = response.json()
    assert body["scope"]["appliedFilter"]["fieldCode"] == selected["field_code"]
    assert body["payload"]["page"]["total"] == 1
    assert {item["fieldCode"] for item in body["payload"]["fields"]} == {
        selected["field_code"]
    }
    assert {item["fieldCode"] for item in body["payload"]["alerts"]} <= {
        selected["field_code"]
    }


def test_data_quality_declares_its_rule_based_assessment(api_factory) -> None:
    _app, client, _spring = api_factory()

    with client:
        response = client.get("/internal/v1/data-quality", headers=HEADERS)

    assert response.status_code == 200, response.text
    assert response.json()["payload"]["assessmentMethod"] == (
        "rule-based-heuristic"
    )


@pytest.mark.parametrize("role", ["FIELD_WORKER", "SUPPLIER"])
def test_non_analytics_personas_are_denied(api_factory, role) -> None:
    _app, client, _spring = api_factory(
        roles={role},
        permissions={"FARM_READ", "COST_READ", "INVENTORY_READ"},
    )

    with client:
        response = client.get("/internal/v1/catalog", headers=HEADERS)

    assert response.status_code == 403
    assert response.json()["error"]["code"] == "analytics_forbidden"


def test_non_demo_tenant_is_denied(api_factory) -> None:
    _app, client, _spring = api_factory(
        tenant_id=UUID("30000000-0000-4000-8000-000000000001")
    )

    with client:
        response = client.get("/internal/v1/overview", headers=HEADERS)

    assert response.status_code == 403
    assert response.json()["error"]["code"] == "demo_tenant_required"


def test_errors_are_typed_and_do_not_leak_paths(api_factory) -> None:
    _app, client, _spring = api_factory()

    with client:
        missing_auth = client.get("/internal/v1/overview")
        invalid_page = client.get(
            "/internal/v1/farms?limit=101",
            headers=HEADERS,
        )
        method = client.post("/internal/v1/overview", headers=HEADERS)

    assert missing_auth.status_code == 401
    assert invalid_page.status_code == 422
    assert method.status_code == 405
    for response in (missing_auth, invalid_page, method):
        assert set(response.json()) == {"correlationId", "error"}
        assert "AgriInsight" not in response.text
        assert "artifacts" not in response.text
