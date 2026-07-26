from __future__ import annotations

from datetime import date, timedelta

import pandas as pd
import pytest

HEADERS = {
    "Authorization": "Bearer test-token",
    "X-Correlation-Id": "phase5-filter-001",
}


def test_overview_applies_verified_canonical_relationship(api_factory) -> None:
    app, client, _spring = api_factory()
    snapshot = app.state.snapshot_cache.current()
    selected = snapshot.csv["cost_season"].iloc[0]

    with client:
        response = client.get(
            "/internal/v1/overview",
            params={
                "farm_code": selected["farm_code"],
                "field_code": selected["field_code"],
                "crop_code": selected["crop_code"],
                "season_code": selected["season_code"],
                "date_preset": "all",
            },
            headers=HEADERS,
        )

    assert response.status_code == 200, response.text
    body = response.json()
    applied = body["scope"]["appliedFilter"]
    assert applied == {
        "cropCode": selected["crop_code"],
        "dateFrom": None,
        "datePreset": "all",
        "dateTo": snapshot.manifest["as_of_date"],
        "farmCode": selected["farm_code"],
        "fieldCode": selected["field_code"],
        "seasonCode": selected["season_code"],
    }
    assert body["payload"]["summary"]["farmCount"] == 1
    assert {
        item["seasonCode"] for item in body["payload"]["topRisks"]
    } <= {selected["season_code"]}


def test_season_to_date_uses_server_event_facts(api_factory) -> None:
    app, client, _spring = api_factory()
    snapshot = app.state.snapshot_cache.current()
    relation = snapshot.csv["cost_season"].iloc[0]
    season_code = relation["season_code"]
    as_of = pd.Timestamp(snapshot.manifest["as_of_date"])

    activities = snapshot.csv["cost_activity_detail"]
    activities = activities[
        (activities["season_code"] == season_code)
        & (pd.to_datetime(activities["occurred_at"]) <= as_of)
    ]
    harvests = snapshot.csv["harvests"]
    harvests = harvests[
        (harvests["season_code"] == season_code)
        & (pd.to_datetime(harvests["harvested_at"]) <= as_of)
    ]

    with client:
        response = client.get(
            "/internal/v1/overview",
            params={
                "season_code": season_code,
                "date_preset": "season-to-date",
            },
            headers=HEADERS,
        )

    assert response.status_code == 200, response.text
    body = response.json()
    summary = body["payload"]["summary"]
    expected_cost = float(activities["operating_total_cost_vnd"].sum())
    expected_revenue = float(harvests["revenue_vnd"].sum())
    assert summary["totalCostVnd"] == expected_cost
    assert summary["totalRevenueVnd"] == expected_revenue
    assert summary["profitVnd"] == expected_revenue - expected_cost
    assert body["scope"]["appliedFilter"]["dateFrom"] == relation["start_date"]
    assert body["scope"]["appliedFilter"]["dateTo"] == str(as_of.date())


def test_last_30_days_returns_resolved_window(api_factory) -> None:
    app, client, _spring = api_factory()
    snapshot = app.state.snapshot_cache.current()
    as_of = date.fromisoformat(snapshot.manifest["as_of_date"])

    with client:
        response = client.get(
            "/internal/v1/farms",
            params={"date_preset": "last-30-days"},
            headers=HEADERS,
        )

    assert response.status_code == 200, response.text
    applied = response.json()["scope"]["appliedFilter"]
    assert applied["dateFrom"] == str(as_of - timedelta(days=29))
    assert applied["dateTo"] == str(as_of)
    assert applied["datePreset"] == "last-30-days"


def test_farm_filter_does_not_double_count_repeated_field_area(
    api_factory,
) -> None:
    app, client, _spring = api_factory()
    snapshot = app.state.snapshot_cache.current()
    expected = snapshot.csv["farm_performance"].iloc[0]

    with client:
        response = client.get(
            "/internal/v1/overview",
            params={"farm_code": expected["farm_code"]},
            headers=HEADERS,
        )

    assert response.status_code == 200, response.text
    summary = response.json()["payload"]["summary"]
    assert summary["cultivatedAreaHa"] == pytest.approx(
        expected["cultivated_area_ha"]
    )
    assert summary["harvestQuantityKg"] == pytest.approx(
        expected["harvest_quantity_kg"]
    )
    assert summary["totalCostVnd"] == pytest.approx(expected["total_cost_vnd"])
    assert summary["totalRevenueVnd"] == pytest.approx(
        expected["total_revenue_vnd"]
    )


def test_filtered_farm_cost_per_ha_uses_season_operated_area(
    api_factory,
) -> None:
    app, client, _spring = api_factory()
    snapshot = app.state.snapshot_cache.current()
    relations = snapshot.csv["cost_season"]
    field_code = str(relations.iloc[0]["field_code"])
    selected = relations[relations["field_code"] == field_code]
    season_codes = frozenset(selected["season_code"].astype(str))
    activities = snapshot.csv["cost_activity_detail"]
    activities = activities[activities["season_code"].isin(season_codes)]
    expected = (
        float(activities["operating_total_cost_vnd"].sum())
        / float(selected["area_ha"].sum())
    )

    with client:
        response = client.get(
            "/internal/v1/farms",
            params={"field_code": field_code},
            headers=HEADERS,
        )

    assert response.status_code == 200, response.text
    assert response.json()["payload"]["items"][0][
        "costVndPerHa"
    ] == pytest.approx(expected)


def test_filter_relationships_fail_closed(api_factory) -> None:
    app, client, _spring = api_factory()
    relations = app.state.snapshot_cache.current().csv["cost_season"]
    first = relations.iloc[0]
    conflicting = relations[relations["field_code"] != first["field_code"]].iloc[0]

    with client:
        unknown = client.get(
            "/internal/v1/overview",
            params={"field_code": "FIELD-FOREIGN"},
            headers=HEADERS,
        )
        conflict = client.get(
            "/internal/v1/overview",
            params={
                "season_code": first["season_code"],
                "field_code": conflicting["field_code"],
            },
            headers=HEADERS,
        )
        missing_season = client.get(
            "/internal/v1/overview",
            params={"date_preset": "season-to-date"},
            headers=HEADERS,
        )

    assert unknown.status_code == 403
    assert unknown.json()["error"]["code"] == "analytics_filter_forbidden"
    assert conflict.status_code == 422
    assert conflict.json()["error"]["code"] == "analytics_filter_conflict"
    assert missing_season.status_code == 422
    assert missing_season.json()["error"]["code"] == "season_filter_required"
