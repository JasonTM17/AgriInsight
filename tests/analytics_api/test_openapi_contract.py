from __future__ import annotations

from pathlib import Path

from agriinsight.analytics_api.openapi_contract import canonical_openapi_bytes

CONTRACT = Path("docs/contracts/agriinsight-analytics-v1.openapi.json")


def _non_null_variant(schema: dict) -> dict:
    return next(
        variant
        for variant in schema["anyOf"]
        if variant.get("type") != "null"
    )


def test_internal_openapi_is_get_only_typed_and_bounded() -> None:
    import json

    contract = json.loads(canonical_openapi_bytes())

    assert set(contract["paths"]) == {
        "/health/ready",
        "/internal/v1/catalog",
        "/internal/v1/costs",
        "/internal/v1/costs/export",
        "/internal/v1/costs/procurement",
        "/internal/v1/crop-health",
        "/internal/v1/data-quality",
        "/internal/v1/farms",
        "/internal/v1/inventory",
        "/internal/v1/overview",
        "/internal/v1/assistant/query",
    }
    assert set(contract["paths"]["/internal/v1/assistant/query"]) == {"post"}
    assert all(
        set(path_item) == {"get"}
        for path, path_item in contract["paths"].items()
        if path != "/internal/v1/assistant/query"
    )
    farm_parameters = contract["paths"]["/internal/v1/farms"]["get"]["parameters"]
    limit = next(item for item in farm_parameters if item["name"] == "limit")
    assert limit["schema"]["maximum"] == 100
    expected_filters = {
        "crop_code",
        "date_preset",
        "farm_code",
        "field_code",
        "season_code",
    }
    overview_parameters = contract["paths"]["/internal/v1/overview"]["get"][
        "parameters"
    ]
    assert expected_filters <= {item["name"] for item in overview_parameters}
    assert expected_filters <= {item["name"] for item in farm_parameters}
    assert contract["components"]["securitySchemes"]["HTTPBearer"]["scheme"] == "bearer"
    schemas = contract["components"]["schemas"]
    record_schemas = {
        name: schema
        for name, schema in schemas.items()
        if name.endswith("Model") and name not in {"EvidenceSignalModel"}
    }
    assert record_schemas
    assert all(
        schema.get("additionalProperties") is False
        for schema in record_schemas.values()
    )
    assert set(schemas["CropHealthPayload"]["properties"]) >= {
        "assessmentMethod",
        "evidenceSignals",
        "severity",
    }
    assert (
        schemas["FieldHealthModel"]["properties"]["lastReadingAt"]["format"]
        == "date-time"
    )
    assert schemas["PestIncidentModel"]["properties"]["week"]["format"] == "date"
    assert set(schemas["DataQualityPayload"]["properties"]) >= {
        "assessmentMethod",
        "evidenceSignals",
        "remediationActions",
        "severity",
    }
    crop_parameters = contract["paths"]["/internal/v1/crop-health"]["get"]["parameters"]
    assert "field_code" in {item["name"] for item in crop_parameters}
    assert set(schemas["AppliedFilterModel"]["properties"]) == {
        "cropCode",
        "dateFrom",
        "datePreset",
        "dateTo",
        "farmCode",
        "fieldCode",
        "seasonCode",
    }
    assert "appliedFilter" in schemas["ScopeModel"]["properties"]
    inventory_payload = schemas["InventoryPayload"]
    assert inventory_payload["properties"]["abc"]["maxItems"] == 100
    assert inventory_payload["properties"]["alerts"]["maxItems"] == 100
    assert inventory_payload["properties"]["items"]["maxItems"] == 100
    assert inventory_payload["properties"]["items"]["items"]["$ref"] == (
        "#/components/schemas/InventoryItemModel"
    )
    forecast = schemas["InventoryForecastModel"]
    assert forecast["additionalProperties"] is False
    assert set(forecast["properties"]) == {
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
    assert set(forecast["required"]) == set(forecast["properties"])
    assert forecast["properties"]["coverageStatus"]["enum"] == [
        "ready",
        "noDemand",
        "insufficientHistory",
        "unavailable",
    ]
    assert _non_null_variant(forecast["properties"]["historyDays"])["minimum"] == 1
    assert _non_null_variant(forecast["properties"]["historyDays"])["maximum"] == 180
    assert _non_null_variant(forecast["properties"]["backtestWindows"])["maximum"] == 9
    assert _non_null_variant(forecast["properties"]["horizonDays"])["const"] == 30
    health = schemas["ForecastHealthModel"]
    assert health["additionalProperties"] is False
    assert set(health["properties"]) == {
        "ready",
        "noDemand",
        "insufficientHistory",
        "unavailable",
        "total",
    }
    assert all(
        health["properties"][name]["minimum"] == 0
        for name in health["properties"]
    )
    abc = schemas["InventoryAbcModel"]["properties"]
    assert abc["valueSharePct"]["maximum"] == 100
    assert abc["cumulativeValueSharePct"]["maximum"] == 100
    for path, path_item in contract["paths"].items():
        operation = (
            path_item["post"]
            if path == "/internal/v1/assistant/query"
            else path_item["get"]
        )
        if path.startswith("/internal/v1/"):
            assert operation["security"] == [{"HTTPBearer": []}]
            assert "401" in operation["responses"]
            assert "503" in operation["responses"]
    assistant_query = schemas["AssistantQuery"]
    assert assistant_query["additionalProperties"] is False
    assert assistant_query["properties"]["question"]["maxLength"] == 1_200
    assert assistant_query["properties"]["history"]["maxItems"] == 6
    assert (
        "429" in contract["paths"]["/internal/v1/assistant/query"]["post"]["responses"]
    )


def test_checked_in_openapi_has_no_drift() -> None:
    assert CONTRACT.read_bytes() == canonical_openapi_bytes()
