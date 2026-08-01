from __future__ import annotations

import os
from pathlib import Path
import subprocess
import sys


ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts" / "run-assistant-provider-evaluation.py"


def test_provider_evaluation_cli_fails_closed_without_secret() -> None:
    environment = os.environ.copy()
    environment.pop("AGRIINSIGHT_LLM_API_KEY", None)
    completed = subprocess.run(
        [sys.executable, str(SCRIPT), "--source-sha", "a" * 40],
        cwd=ROOT,
        env=environment,
        capture_output=True,
        text=True,
        check=False,
        timeout=10,
    )

    assert completed.returncode != 0
    assert completed.stdout == ""
    assert completed.stderr.strip() == "assistant provider evaluation failed"
