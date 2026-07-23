from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Sequence

from agriinsight.analytics_snapshot import ArtifactSnapshot
from agriinsight.demo_tenant_bootstrap_sql import DemoSqlBundle, build_sql_bundle
from agriinsight.demo_tenant_contract import (
    DemoContract,
    load_demo_contract,
    load_demo_snapshot,
    validate_persona_scopes,
)

_REPOSITORY_TMP = Path(__file__).resolve().parents[2] / "_tmp"


def create_demo_bundle(
    artifact_root: Path,
    contract_path: Path,
    *,
    sample_activity_limit: int = 24,
) -> DemoSqlBundle:
    contract, snapshot = _load_demo_inputs(artifact_root, contract_path)
    return build_sql_bundle(
        contract,
        snapshot,
        sample_activity_limit=sample_activity_limit,
    )


def write_demo_bundle(
    artifact_root: Path,
    contract_path: Path,
    output_directory: Path,
    *,
    confirmed: bool,
    sample_activity_limit: int = 24,
) -> dict[str, object]:
    if not confirmed:
        raise ValueError("Explicit local-demo confirmation is required")
    output = _validated_output_directory(output_directory)
    contract, snapshot = _load_demo_inputs(artifact_root, contract_path)
    bundle = build_sql_bundle(
        contract,
        snapshot,
        sample_activity_limit=sample_activity_limit,
    )
    output.mkdir(parents=True, exist_ok=True)
    (output / "seed.sql").write_text(bundle.seed_sql, encoding="utf-8")
    (output / "inspect.sql").write_text(bundle.inspection_sql, encoding="utf-8")
    metadata = {
        "demoTenantId": str(contract.tenant_id),
        "manifestFingerprint": snapshot.manifest_fingerprint,
        "runId": str(snapshot.manifest.get("run_id", "")),
        "sampleActivityCount": bundle.sample_activity_count,
        "status": "generated",
    }
    (output / "bundle.json").write_text(
        json.dumps(metadata, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    return metadata


def _load_demo_inputs(
    artifact_root: Path,
    contract_path: Path,
) -> tuple[DemoContract, ArtifactSnapshot]:
    contract = load_demo_contract(contract_path)
    snapshot = load_demo_snapshot(artifact_root)
    validate_persona_scopes(contract, snapshot)
    return contract, snapshot


def _validated_output_directory(path: Path) -> Path:
    resolved = path.resolve()
    expected_root = _REPOSITORY_TMP.resolve()
    if (
        resolved.drive.upper() != "D:"
        or resolved == expected_root
        or not resolved.is_relative_to(expected_root)
    ):
        raise ValueError(
            "Demo bundle output must be below the repository D-local _tmp directory"
        )
    return resolved


def main(arguments: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="Generate a credential-free local-demo PostgreSQL bundle."
    )
    parser.add_argument("--artifact-root", type=Path, required=True)
    parser.add_argument("--contract", type=Path, required=True)
    parser.add_argument("--output-directory", type=Path, required=True)
    parser.add_argument("--sample-activity-limit", type=int, default=24)
    parser.add_argument("--confirm-local-demo", action="store_true")
    values = parser.parse_args(arguments)
    metadata = write_demo_bundle(
        values.artifact_root,
        values.contract,
        values.output_directory,
        confirmed=values.confirm_local_demo,
        sample_activity_limit=values.sample_activity_limit,
    )
    print(json.dumps(metadata, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
