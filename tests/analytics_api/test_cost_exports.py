from __future__ import annotations

import json

import agriinsight.cost_report_single_export as single_export
from agriinsight.cost_report_contract import ExportUnavailable

HEADERS = {
    "Authorization": "Bearer test-token",
    "X-Correlation-Id": "phase8-export-001",
}
METADATA_HEADER = "X-AgriInsight-Export-Metadata"


def _unavailable_runtime() -> object:
    raise ExportUnavailable("XLSX runtime is not provisioned")


def test_csv_export_streams_a_file_with_safe_metadata(api_factory, monkeypatch) -> None:
    monkeypatch.setattr(single_export, "detect_xlsx_runtime", _unavailable_runtime)
    _app, client, _spring = api_factory()

    with client:
        response = client.get(
            "/internal/v1/costs/export?format=csv", headers=HEADERS
        )

    assert response.status_code == 200, response.text
    assert response.headers["content-type"].startswith("text/csv")
    assert response.content.startswith(b"\xef\xbb\xbf")
    disposition = response.headers["content-disposition"]
    assert disposition.startswith('attachment; filename="')
    assert disposition.endswith('.csv"')

    metadata = json.loads(response.headers[METADATA_HEADER])
    assert set(metadata) == {
        "asOf",
        "byteSize",
        "checksumSha256",
        "contractVersion",
        "filename",
        "filterHash",
        "format",
        "rowCount",
        "runId",
    }
    assert metadata["format"] == "csv"
    assert metadata["byteSize"] == len(response.content)
    assert metadata["rowCount"] >= 0


def test_csv_export_never_initializes_the_xlsx_runtime(api_factory, monkeypatch) -> None:
    def _explode() -> object:
        raise AssertionError("CSV export must not initialize the XLSX runtime")

    monkeypatch.setattr(single_export, "detect_xlsx_runtime", _explode)
    _app, client, _spring = api_factory()

    with client:
        response = client.get(
            "/internal/v1/costs/export?format=csv", headers=HEADERS
        )

    assert response.status_code == 200, response.text


def test_export_metadata_and_errors_never_leak_filesystem_paths(
    api_factory, monkeypatch
) -> None:
    monkeypatch.setattr(single_export, "detect_xlsx_runtime", _unavailable_runtime)
    app, client, _spring = api_factory()
    artifact_root = str(app.state.settings.artifact_root)

    with client:
        response = client.get(
            "/internal/v1/costs/export?format=csv", headers=HEADERS
        )

    assert response.status_code == 200, response.text
    assert artifact_root not in response.headers[METADATA_HEADER]
    assert "_tmp" not in response.headers[METADATA_HEADER]
    assert artifact_root not in response.headers["content-disposition"]


def test_unknown_format_is_refused_without_rendering(api_factory) -> None:
    _app, client, _spring = api_factory()

    with client:
        response = client.get(
            "/internal/v1/costs/export?format=json", headers=HEADERS
        )

    assert response.status_code == 422, response.text
    body = response.json()
    assert body["error"]["code"] == "export_rejected"
    assert "csv" in body["error"]["message"]


def test_missing_format_is_refused(api_factory) -> None:
    _app, client, _spring = api_factory()

    with client:
        response = client.get("/internal/v1/costs/export", headers=HEADERS)

    assert response.status_code == 422, response.text


def test_oversized_export_reports_applied_filters(api_factory, monkeypatch) -> None:
    monkeypatch.setattr(single_export, "MAX_BUNDLE_BYTES", 10)
    monkeypatch.setattr(single_export, "detect_xlsx_runtime", _unavailable_runtime)
    _app, client, _spring = api_factory()

    with client:
        response = client.get(
            "/internal/v1/costs/export?format=csv&month_from=2025-01",
            headers=HEADERS,
        )

    assert response.status_code == 422, response.text
    message = response.json()["error"]["message"]
    assert "exceeds" in message
    assert "Narrow the filters" in message
    assert "month_from=2025-01" in message


def test_unavailable_xlsx_runtime_returns_a_typed_service_problem(
    api_factory, monkeypatch
) -> None:
    monkeypatch.setattr(single_export, "detect_xlsx_runtime", _unavailable_runtime)
    _app, client, _spring = api_factory()

    with client:
        response = client.get(
            "/internal/v1/costs/export?format=xlsx", headers=HEADERS
        )

    assert response.status_code == 503, response.text
    body = response.json()
    assert body["error"]["code"] == "export_format_unavailable"
    assert "csv or pdf" in body["error"]["message"]


def test_export_rejects_a_farm_outside_the_authorized_scope(api_factory) -> None:
    _app, client, _spring = api_factory()

    with client:
        response = client.get(
            "/internal/v1/costs/export?format=csv&farm=FARM-NOT-MINE",
            headers=HEADERS,
        )

    assert response.status_code == 403, response.text
    assert response.json()["error"]["code"] == "farm_scope_forbidden"


def test_export_rejects_inverted_month_window(api_factory) -> None:
    _app, client, _spring = api_factory()

    with client:
        response = client.get(
            "/internal/v1/costs/export?format=csv&month_from=2025-06&month_to=2025-01",
            headers=HEADERS,
        )

    assert response.status_code == 422, response.text
    assert response.json()["error"]["code"] == "invalid_request"


def test_export_scopes_rows_to_a_single_requested_farm(api_factory, monkeypatch) -> None:
    monkeypatch.setattr(single_export, "detect_xlsx_runtime", _unavailable_runtime)
    app, client, spring = api_factory()
    selected = spring.farms[0].code

    with client:
        scoped = client.get(
            f"/internal/v1/costs/export?format=csv&farm={selected}",
            headers=HEADERS,
        )
        everything = client.get(
            "/internal/v1/costs/export?format=csv", headers=HEADERS
        )

    assert scoped.status_code == 200, scoped.text
    assert everything.status_code == 200, everything.text
    scoped_rows = json.loads(scoped.headers[METADATA_HEADER])["rowCount"]
    all_rows = json.loads(everything.headers[METADATA_HEADER])["rowCount"]
    assert 0 < scoped_rows <= all_rows
    assert app.state.snapshot_cache.current() is not None
