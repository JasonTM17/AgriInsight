from __future__ import annotations

HEADERS = {
    "Authorization": "Bearer test-token",
    "X-Correlation-Id": "phase8-costs-001",
}


def test_procurement_costs_returns_only_procurement_lens(api_factory) -> None:
    _app, client, _spring = api_factory()

    with client:
        response = client.get(
            "/internal/v1/costs/procurement?limit=3",
            headers=HEADERS,
        )

    assert response.status_code == 200, response.text
    payload = response.json()["payload"]
    assert payload["capabilities"] == {
        "readOnly": True,
        "fileExportAvailable": True,
        "detailPageAvailable": True,
    }
    assert payload["page"]["limit"] == 3
    assert len(payload["items"]) <= 3
    assert payload["summary"]["transactionCount"] > 0
    assert payload["summary"]["procurementSpendVnd"] > 0
    assert payload["monthly"]
    assert payload["suppliers"]
    assert response.json()["scope"]["tenantWide"] is True


def test_procurement_costs_applies_month_and_farm_scope(api_factory) -> None:
    app, client, spring = api_factory()
    selected = spring.farms[0].code
    snapshot = app.state.snapshot_cache.current()
    expected = snapshot.csv["procurement_detail"]
    expected = expected[
        (expected["farm_code"] == selected)
        & (expected["month"] >= "2025-01")
        & (expected["month"] <= "2025-03")
    ]

    with client:
        response = client.get(
            (
                "/internal/v1/costs/procurement"
                f"?farm_code={selected}&month_from=2025-01&month_to=2025-03"
            ),
            headers=HEADERS,
        )

    assert response.status_code == 200
    payload = response.json()["payload"]
    assert payload["summary"]["transactionCount"] == len(expected)
    assert payload["summary"]["procurementSpendVnd"] == expected[
        "procurement_spend_vnd"
    ].sum()
    assert all(item["farmCode"] == selected for item in payload["items"])


def test_procurement_costs_rejects_foreign_and_inverted_filters(api_factory) -> None:
    app, client, _spring = api_factory()

    class ExplodingCache:
        def current(self):
            raise AssertionError("artifact must not be accessed")

    app.state.snapshot_cache = ExplodingCache()
    with client:
        foreign = client.get(
            "/internal/v1/costs/procurement?farm_code=FARM-FOREIGN",
            headers=HEADERS,
        )
        inverted = client.get(
            (
                "/internal/v1/costs/procurement"
                "?month_from=2025-04&month_to=2025-01"
            ),
            headers=HEADERS,
        )

    assert foreign.status_code == 403
    assert foreign.json()["error"]["code"] == "farm_scope_forbidden"
    assert inverted.status_code == 422
    assert inverted.json()["error"]["code"] == "invalid_request"
