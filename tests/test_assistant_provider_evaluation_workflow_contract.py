from __future__ import annotations

import re
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
WORKFLOW_PATH = ROOT / ".github" / "workflows" / "assistant-provider-evaluation.yml"
CI_PATH = ROOT / ".github" / "workflows" / "ci.yml"


def _workflow() -> str:
    return WORKFLOW_PATH.read_text(encoding="utf-8")


def _named_step(workflow: str, name: str, next_name: str | None = None) -> str:
    start_marker = f"      - name: {name}"
    start = workflow.index(start_marker)
    if next_name is None:
        return workflow[start:]
    end = workflow.index(f"      - name: {next_name}", start)
    return workflow[start:end]


def test_workflow_is_manual_read_only_serial_and_single_job() -> None:
    workflow = _workflow()
    trigger = workflow[workflow.index("\non:") : workflow.index("\npermissions:")]

    assert trigger == "\non:\n  workflow_dispatch:\n"
    assert all(
        forbidden not in trigger
        for forbidden in ("push:", "pull_request:", "schedule:", "release:")
    )
    assert "permissions:\n  contents: read\n" in workflow
    assert "cancel-in-progress: false" in workflow
    assert re.findall(r"^  [a-z][a-z0-9-]*:\s*$", workflow, re.MULTILINE) == [
        "  evaluate:"
    ]
    assert "runs-on: ubuntu-latest" in workflow
    assert "timeout-minutes: 15" in workflow
    assert "environment: assistant-provider-evaluation" in workflow
    assert "docker" not in workflow.lower()


def test_workflow_pins_ci_matching_checkout_python_and_locked_install() -> None:
    workflow = _workflow()
    ci = CI_PATH.read_text(encoding="utf-8")
    checkout = (
        "actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1"
    )
    setup_python = (
        "actions/setup-python@5fda3b95a4ea91299a34e894583c3862153e4b97"
    )

    for action in (checkout, setup_python):
        assert workflow.count(action) == 1
        assert action in ci
    assert 'python-version: "3.13"' in workflow
    assert 'python -m pip install ".[dev]"' in workflow


def test_exact_sha_is_verified_before_the_secret_scoped_run_step() -> None:
    workflow = _workflow()
    verify_step = _named_step(
        workflow,
        "Verify the exact checked-out source",
        "Run the bounded provider evaluation",
    )
    evaluation_step = _named_step(
        workflow,
        "Run the bounded provider evaluation",
        "Validate the aggregate and protected gates",
    )
    secret_reference = "${{ secrets.AGRIINSIGHT_LLM_API_KEY }}"

    assert 'checked_out_sha="$(git rev-parse HEAD)"' in verify_step
    assert 'test "$checked_out_sha" = "$GITHUB_SHA"' in verify_step
    assert "secrets." not in verify_step
    assert workflow.index("Verify the exact checked-out source") < workflow.index(
        secret_reference
    )
    assert workflow.count(secret_reference) == 1
    assert evaluation_step.count("secrets.") == 1
    assert (
        "env:\n          AGRIINSIGHT_LLM_API_KEY: " + secret_reference
        in evaluation_step
    )


def test_evaluation_uses_fixed_nonsecret_controls_and_temp_aggregate_only() -> None:
    workflow = _workflow()
    evaluation_step = _named_step(
        workflow,
        "Run the bounded provider evaluation",
        "Validate the aggregate and protected gates",
    )

    assert "scripts/run-assistant-provider-evaluation.py" in evaluation_step
    assert '--source-sha "$GITHUB_SHA"' in evaluation_step
    assert "--repetitions 2" in evaluation_step
    assert "--concurrency 3" in evaluation_step
    assert (
        'aggregate_path="$RUNNER_TEMP/assistant-provider-evaluation.json"'
        in evaluation_step
    )
    assert '> "$aggregate_path"' in evaluation_step
    assert all(
        unsafe not in evaluation_step
        for unsafe in ("set -x", "tee ", "cat ", "echo ", "printenv", "env |")
    )


def test_validation_checks_exact_source_and_every_protected_gate_without_secret() -> None:
    workflow = _workflow()
    validation_step = _named_step(
        workflow,
        "Validate the aggregate and protected gates",
        "Upload the aggregate evidence",
    )

    assert "secrets." not in validation_step
    assert 'aggregate["source_sha"] == expected_sha' in validation_step
    assert 'aggregate["sample_count"] == 30' in validation_step
    assert 'aggregate["repetitions"] == 2' in validation_step
    assert 'aggregate["concurrency"] == 3' in validation_step
    assert 'aggregate["provider_expected_count"] == 20' in validation_step
    assert 'aggregate["provider_call_count"] == 20' in validation_step
    assert 'aggregate["refusal_expected_count"] == 10' in validation_step
    assert 'aggregate["gates"]["passed"] is True' in validation_step
    assert "if set(aggregate) != expected_keys:" in validation_step
    for aggregate_only_key in (
        '"fixture_version"',
        '"outcome_counts"',
        '"pricing"',
        '"estimated_cost_usd"',
        '"maximum_possible_cost_usd"',
    ):
        assert aggregate_only_key in validation_step
    for gate in (
        'closed_corpus["pass_rate"] == 1.0',
        'citations["precision"] == 1.0',
        'refusals["precision"] == 1.0',
        'usage["total_tokens"] <= 200_000',
        'aggregate["provider_p95_completed_response_ms"] <= 12_000',
    ):
        assert gate in validation_step
    assert "json.load(aggregate_file)" in validation_step
    assert all(
        unsafe not in validation_step
        for unsafe in ("set -x", "tee ", "cat ", "echo ", "print(aggregate")
    )


def test_upload_contains_only_the_single_aggregate_with_bounded_retention() -> None:
    workflow = _workflow()
    upload_step = _named_step(workflow, "Upload the aggregate evidence")
    upload_action = (
        "actions/upload-artifact@ea165f8d65b6e75b540449e92b4886f43607fa02"
    )

    assert upload_action in upload_step
    assert (
        "path: ${{ runner.temp }}/assistant-provider-evaluation.json"
        in upload_step
    )
    assert upload_step.count("path:") == 1
    assert "*" not in upload_step
    assert "if-no-files-found: error" in upload_step
    assert "retention-days: 7" in upload_step
    assert "secrets." not in upload_step


def test_normal_ci_never_receives_provider_credentials_or_evaluation_workload() -> None:
    ci = CI_PATH.read_text(encoding="utf-8")

    assert "AGRIINSIGHT_LLM_API_KEY" not in ci
    assert "secrets.AGRIINSIGHT_LLM_API_KEY" not in ci
    assert "run-assistant-provider-evaluation.py" not in ci
    assert "assistant-provider-evaluation" not in ci
