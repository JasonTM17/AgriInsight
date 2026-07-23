from __future__ import annotations

import argparse
import json
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Mapping, Sequence

from agriinsight.analytics_snapshot import ArtifactSnapshot
from agriinsight.demo_tenant_contract import (
    DemoContract,
    load_demo_contract,
    load_demo_snapshot,
)

_DOMAINS = ("farms", "fields", "crops", "seasons", "warehouses", "materials")
_REPOSITORY_TMP = Path(__file__).resolve().parents[2] / "_tmp"
MAX_CATALOG_BYTES = 8 * 1024 * 1024


def expected_catalog(snapshot: ArtifactSnapshot) -> dict[str, list[dict[str, Any]]]:
    return {
        "farms": [
            {"code": row.farm_code, "active": True}
            for row in snapshot.csv["farms"].itertuples(index=False)
        ],
        "fields": [
            {
                "code": row.field_code,
                "active": True,
                "farmCode": row.farm_code,
            }
            for row in snapshot.csv["fields"].itertuples(index=False)
        ],
        "crops": [
            {"code": row.crop_code, "active": True}
            for row in snapshot.csv["crops"].itertuples(index=False)
        ],
        "seasons": [
            {
                "code": row.season_code,
                "active": True,
                "fieldCode": row.field_code,
                "cropCode": row.crop_code,
            }
            for row in snapshot.csv["seasons"].itertuples(index=False)
        ],
        "warehouses": [
            {"code": row.warehouse_code, "active": True}
            for row in snapshot.csv["warehouses"].itertuples(index=False)
        ],
        "materials": [
            {"code": row.material_code, "active": True}
            for row in snapshot.csv["materials"].itertuples(index=False)
        ],
    }


def expected_personas(contract: DemoContract) -> list[dict[str, Any]]:
    return [
        {
            "active": True,
            "farmCodes": sorted(persona.farm_codes),
            "issuer": contract.issuer,
            "profileId": str(persona.profile_id),
            "role": persona.role,
            "subject": persona.subject,
            "warehouseCodes": sorted(persona.warehouse_codes),
        }
        for persona in contract.personas
    ]


def expected_operational_state(
    contract: DemoContract,
    snapshot: ArtifactSnapshot,
) -> dict[str, list[dict[str, Any]]]:
    return {
        **expected_catalog(snapshot),
        "personas": expected_personas(contract),
    }


def reconcile_catalog(
    contract: DemoContract,
    snapshot: ArtifactSnapshot,
    actual: Mapping[str, Any],
) -> dict[str, Any]:
    expected = expected_catalog(snapshot)
    domain_reports: dict[str, Any] = {}
    error_count = 0
    for domain in _DOMAINS:
        report = _reconcile_domain(expected[domain], actual.get(domain))
        domain_reports[domain] = report
        error_count += len(report["errors"])
    persona_report = _reconcile_personas(contract, actual.get("personas"))
    domain_reports["personas"] = persona_report
    error_count += len(persona_report["errors"])
    report_domains = (*_DOMAINS, "personas")
    return {
        "counts": {
            domain: domain_reports[domain]["expectedCount"]
            for domain in report_domains
        },
        "demoTenantId": str(contract.tenant_id),
        "domains": domain_reports,
        "errorCount": error_count,
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "manifestFingerprint": snapshot.manifest_fingerprint,
        "runId": str(snapshot.manifest.get("run_id", "")),
        "status": "passed" if error_count == 0 else "failed",
    }


def write_reconciliation_report(
    artifact_root: Path,
    contract_path: Path,
    actual_path: Path,
    output_path: Path,
) -> dict[str, Any]:
    output = output_path.resolve()
    expected_root = _REPOSITORY_TMP.resolve()
    if (
        output.drive.upper() != "D:"
        or not output.is_relative_to(expected_root)
    ):
        raise ValueError(
            "Reconciliation output must be under the repository D-local _tmp directory"
    )
    try:
        if actual_path.stat().st_size > MAX_CATALOG_BYTES:
            raise ValueError("Operational catalog exceeds the safe byte limit")
        actual = json.loads(actual_path.read_text(encoding="utf-8-sig"))
    except (OSError, UnicodeError, ValueError, json.JSONDecodeError) as error:
        raise ValueError("Operational catalog JSON is unreadable") from error
    if not isinstance(actual, dict):
        raise ValueError("Operational catalog root must be an object")
    snapshot = load_demo_snapshot(artifact_root)
    contract = load_demo_contract(contract_path)
    report = reconcile_catalog(contract, snapshot, actual)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(
        json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    return report


def _reconcile_domain(
    expected: list[dict[str, Any]],
    actual: Any,
) -> dict[str, Any]:
    if not isinstance(actual, list) or any(
        not isinstance(item, dict) for item in actual
    ):
        return {
            "actualCount": 0,
            "errors": ["operational payload is not a record array"],
            "expectedCount": len(expected),
        }
    expected_by_code: dict[str, dict[str, Any]] = {}
    duplicate_expected_codes: set[str] = set()
    for item in expected:
        code = item["code"]
        if code in expected_by_code:
            duplicate_expected_codes.add(code)
        expected_by_code[code] = item
    actual_by_code: dict[str, list[dict[str, Any]]] = {}
    invalid_rows = 0
    for item in actual:
        code = item.get("code")
        if not isinstance(code, str) or not code:
            invalid_rows += 1
            continue
        actual_by_code.setdefault(code, []).append(item)
    errors: list[str] = []
    errors.extend(
        f"{code}: duplicate expected canonical code"
        for code in sorted(duplicate_expected_codes)
    )
    if invalid_rows:
        errors.append(f"{invalid_rows} rows have invalid codes")
    if len(actual) != len(expected):
        errors.append(
            f"expected {len(expected)} operational rows, got {len(actual)}"
        )
    expected_codes = set(expected_by_code)
    actual_codes = set(actual_by_code)
    for code in sorted(expected_codes - actual_codes):
        errors.append(f"{code}: missing")
    for code in sorted(actual_codes - expected_codes):
        errors.append(f"{code}: unexpected")
    for code in sorted(expected_codes & actual_codes):
        rows = actual_by_code[code]
        if len(rows) != 1:
            errors.append(f"{code}: expected one operational row, got {len(rows)}")
            continue
        actual_row = rows[0]
        for key, expected_value in expected_by_code[code].items():
            if actual_row.get(key) != expected_value:
                errors.append(
                    f"{code}: {key} expected {expected_value!r}, "
                    f"got {actual_row.get(key)!r}"
                )
    return {
        "actualCount": len(actual),
        "errors": errors,
        "expectedCount": len(expected),
    }


def _reconcile_personas(
    contract: DemoContract,
    actual: Any,
) -> dict[str, Any]:
    expected = expected_personas(contract)
    if isinstance(actual, list):
        normalized = [
            {
                **item,
                "farmCodes": sorted(item.get("farmCodes", [])),
                "warehouseCodes": sorted(item.get("warehouseCodes", [])),
            }
            for item in actual
            if isinstance(item, dict)
        ]
    else:
        normalized = actual
    keyed_expected = [{**item, "code": item["profileId"]} for item in expected]
    keyed_actual = (
        [{**item, "code": item.get("profileId")} for item in normalized]
        if isinstance(normalized, list)
        else normalized
    )
    return _reconcile_domain(keyed_expected, keyed_actual)


def main(arguments: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="Reconcile verified analytic masters with local demo PostgreSQL."
    )
    parser.add_argument("--artifact-root", type=Path, required=True)
    parser.add_argument("--contract", type=Path, required=True)
    parser.add_argument("--actual-json", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    values = parser.parse_args(arguments)
    report = write_reconciliation_report(
        values.artifact_root,
        values.contract,
        values.actual_json,
        values.output,
    )
    print(json.dumps({"errorCount": report["errorCount"], "status": report["status"]}))
    return 0 if report["status"] == "passed" else 2


if __name__ == "__main__":
    raise SystemExit(main())
