from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Sequence
from uuid import UUID

from agriinsight.analytics_api.app import create_app
from agriinsight.analytics_api.assistant_settings import AssistantSettings
from agriinsight.analytics_api.settings import AnalyticsSettings


class _ContractSpringClient:
    async def close(self) -> None:
        return None


def canonical_openapi_bytes() -> bytes:
    settings = AnalyticsSettings(
        artifact_root=Path(".").resolve(),
        demo_tenant_id=UUID("20000000-0000-4000-8000-000000000001"),
        reconciliation_report=Path("reconciliation.json").resolve(),
        spring_base_url="http://spring.invalid",
        assistant=AssistantSettings(
            enabled=True,
            api_key="contract-only-key-material-000000",
        ),
    )
    contract = create_app(
        settings,
        spring_client=_ContractSpringClient(),
        assistant_service=object(),
    ).openapi()
    return (
        json.dumps(
            contract,
            ensure_ascii=False,
            indent=2,
            sort_keys=True,
        )
        + "\n"
    ).encode("utf-8")


def main(arguments: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="Generate the deterministic internal analytics OpenAPI contract."
    )
    parser.add_argument("--output", type=Path, required=True)
    values = parser.parse_args(arguments)
    values.output.parent.mkdir(parents=True, exist_ok=True)
    values.output.write_bytes(canonical_openapi_bytes())
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
