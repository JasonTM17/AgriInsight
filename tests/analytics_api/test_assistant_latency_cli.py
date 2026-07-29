from __future__ import annotations

import json
from pathlib import Path
import subprocess
import sys


ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts" / "run-assistant-latency-evaluation.py"


def test_mock_latency_cli_emits_one_redacted_aggregate() -> None:
    completed = subprocess.run(
        [sys.executable, str(SCRIPT)],
        capture_output=True,
        check=True,
        cwd=ROOT,
        text=True,
        timeout=10,
    )

    output_lines = completed.stdout.splitlines()
    assert len(output_lines) == 1
    payload = json.loads(output_lines[0])
    assert payload == {
        "outcome_counts": {
            "answered": 2,
            "error": 2,
            "insufficient_evidence": 2,
        },
        "p50_ms": payload["p50_ms"],
        "p95_ms": payload["p95_ms"],
        "sample_count": 6,
    }
    assert isinstance(payload["p50_ms"], int)
    assert isinstance(payload["p95_ms"], int)
    assert payload["p50_ms"] >= 0
    assert payload["p95_ms"] >= payload["p50_ms"]
    assert completed.stderr == ""

    output = completed.stdout
    for sensitive_value in (
        "local-evaluation-",
        "Verified local result.",
        "FARM-01",
        "mock_provider_failure",
        "40000000-0000-4000-8000-000000000001",
    ):
        assert sensitive_value not in output
