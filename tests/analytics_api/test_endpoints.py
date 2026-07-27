from __future__ import annotations

import json
from datetime import date, datetime, timedelta, timezone
from uuid import UUID

import pytest

HEADERS = {
    "Authorization": "Bearer test-token",
    "X-Correlation-Id": "phase2-test-001",
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
    assert len(responses[3].json()["payload"]["items"]) <= 2
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


@pytest.mark.parametrize(
    "path",
    [
        "/internal/v1/farms?farm_code=FARM-FOREIGN",
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
