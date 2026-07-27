from __future__ import annotations

from pathlib import Path

from agriinsight.analytics_api.openapi_contract import canonical_openapi_bytes

CONTRACT = Path("docs/contracts/agriinsight-analytics-v1.openapi.json")


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
    }
    assert all(set(path_item) == {"get"} for path_item in contract["paths"].values())
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
    assert expected_filters <= {
        item["name"] for item in overview_parameters
    }
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
    crop_parameters = contract["paths"]["/internal/v1/crop-health"]["get"][
        "parameters"
    ]
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
    for path, path_item in contract["paths"].items():
        operation = path_item["get"]
        if path.startswith("/internal/v1/"):
            assert operation["security"] == [{"HTTPBearer": []}]
            assert "401" in operation["responses"]
            assert "503" in operation["responses"]


def test_checked_in_openapi_has_no_drift() -> None:
    assert CONTRACT.read_bytes() == canonical_openapi_bytes()
