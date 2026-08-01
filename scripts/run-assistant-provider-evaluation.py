"""Run the approval-gated, aggregate-only DeepSeek assistant evaluation."""

from __future__ import annotations

import argparse
import asyncio
import json
import os
from pathlib import Path
import sys


PROJECT_SOURCE = Path(__file__).resolve().parents[1] / "src"
if str(PROJECT_SOURCE) not in sys.path:
    sys.path.insert(0, str(PROJECT_SOURCE))

from agriinsight.analytics_api.assistant_provider_evaluation_workload import (
    run_provider_evaluation,
)
from agriinsight.analytics_api.assistant_settings import AssistantSettings


def main() -> None:
    arguments = _arguments()
    token = os.environ.get("AGRIINSIGHT_LLM_API_KEY", "").strip()
    if not token:
        raise SystemExit("assistant provider evaluation failed")
    try:
        settings = AssistantSettings.from_environment(
            {
                "AGRIINSIGHT_ASSISTANT_ENABLED": "true",
                "AGRIINSIGHT_LLM_PROVIDER": "deepseek",
                "AGRIINSIGHT_LLM_BASE_URL": "https://api.deepseek.com",
                "AGRIINSIGHT_LLM_MODEL": "deepseek-v4-flash",
                "AGRIINSIGHT_LLM_API_KEY": token,
                "AGRIINSIGHT_LLM_THINKING_ENABLED": "false",
                "AGRIINSIGHT_LLM_CONNECT_TIMEOUT_SECONDS": "3",
                "AGRIINSIGHT_LLM_READ_TIMEOUT_SECONDS": "25",
                "AGRIINSIGHT_LLM_QUEUE_TIMEOUT_SECONDS": "2",
            }
        )
        summary = asyncio.run(
            run_provider_evaluation(
                settings,
                source_sha=arguments.source_sha,
                fixture_path=arguments.fixture,
                repetitions=arguments.repetitions,
                concurrency=arguments.concurrency,
            )
        )
        payload = summary.to_dict()
        print(json.dumps(payload, ensure_ascii=False, sort_keys=True, separators=(",", ":")))
        if not payload["gates"]["passed"]:
            raise SystemExit("assistant provider evaluation gates failed")
    except SystemExit:
        raise
    except Exception:
        raise SystemExit("assistant provider evaluation failed") from None


def _arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-sha", required=True)
    parser.add_argument(
        "--fixture",
        type=Path,
        default=Path(__file__).resolve().parents[1]
        / "tests"
        / "fixtures"
        / "assistant-retrieval-evaluation-v1.json",
    )
    parser.add_argument("--repetitions", type=int, default=2)
    parser.add_argument("--concurrency", type=int, default=3)
    return parser.parse_args()


if __name__ == "__main__":
    main()

