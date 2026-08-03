from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CI_WORKFLOW = ROOT / ".github" / "workflows" / "ci.yml"
REALTIME_RUNNER = ROOT / "scripts" / "run-realtime-e2e-tests.ps1"


def _realtime_step() -> str:
    workflow = CI_WORKFLOW.read_text(encoding="utf-8")
    step_start = workflow.index(
        "- name: Run real PostgreSQL, Apache Kafka, recovery, replay, DLT, and RLS gate"
    )
    step_end = workflow.index("\n\n  browser-e2e:", step_start)
    return workflow[step_start:step_end]


def test_realtime_e2e_accepts_only_setup_java_maven_args() -> None:
    realtime_step = _realtime_step()

    guard = 'if ($env:MAVEN_ARGS -cne "-ntp")'
    cleanup = "Remove-Item -Path Env:MAVEN_ARGS"
    runner = "./scripts/run-realtime-e2e-tests.ps1 -HostedCi"

    assert guard in realtime_step
    assert cleanup in realtime_step
    assert realtime_step.index(guard) < realtime_step.index(cleanup)
    assert realtime_step.index(cleanup) < realtime_step.index(runner)


def test_realtime_e2e_keeps_other_maven_environment_fail_closed() -> None:
    realtime_step = _realtime_step()
    runner = REALTIME_RUNNER.read_text(encoding="utf-8")

    assert "Remove-Item -Path Env:MAVEN_CONFIG" not in realtime_step
    assert "Remove-Item -Path Env:MAVEN_PROJECTBASEDIR" not in realtime_step
    assert "$env:MAVEN_CONFIG" in runner
    assert "$env:MAVEN_PROJECTBASEDIR" in runner
