from __future__ import annotations

import re
import shutil
import subprocess
from pathlib import Path

import pytest


PROJECT_ROOT = Path(__file__).resolve().parents[1]
DISK_GUARD = PROJECT_ROOT / "scripts" / "check-workspace-disk.ps1"
POWERSHELL = (
    shutil.which("powershell.exe")
    or shutil.which("powershell")
    or shutil.which("pwsh")
)


def _run_guard(*arguments: object) -> subprocess.CompletedProcess[str]:
    if POWERSHELL is None:
        pytest.skip("PowerShell is not available")

    return subprocess.run(
        [
            POWERSHELL,
            "-NoLogo",
            "-NoProfile",
            "-NonInteractive",
            "-ExecutionPolicy",
            "Bypass",
            "-File",
            str(DISK_GUARD),
            *(str(argument) for argument in arguments),
        ],
        cwd=PROJECT_ROOT,
        capture_output=True,
        check=False,
        text=True,
        timeout=30,
    )


def _output(result: subprocess.CompletedProcess[str]) -> str:
    return "\n".join(part for part in (result.stdout, result.stderr) if part)


def _assert_drive_status(
    result: subprocess.CompletedProcess[str], drive: str, status: str
) -> None:
    pattern = rf"(?m)^DISK_GUARD drive={drive} .* status={status} source=test-only$"
    assert re.search(pattern, result.stdout), _output(result)


def test_script_contains_read_only_actual_drive_checks() -> None:
    source = DISK_GUARD.read_text(encoding="utf-8")
    source_lower = source.lower()

    assert "Get-PSDrive" in source
    assert 'Letter = "C"' in source
    assert 'Letter = "D"' in source
    assert 'observationSource = "actual"' in source

    destructive_tokens = (
        "remove-item",
        "clear-content",
        "set-content",
        "out-file",
        "format-volume",
        "remove-psdrive",
        "[system.io.file]::delete",
        "[system.io.directory]::delete",
    )
    assert not any(token in source_lower for token in destructive_tokens)
    assert not re.search(
        r"(?im)(?:^|[;&|]\s*)(?:rm|del|erase|rmdir|rd)\s+", source
    )


def test_pass_at_warning_boundaries() -> None:
    result = _run_guard(
        "-TestOnly",
        "-TestOnlyCFreeGb",
        10,
        "-TestOnlyDFreeGb",
        25,
    )

    assert result.returncode == 0, _output(result)
    _assert_drive_status(result, "C", "PASS")
    _assert_drive_status(result, "D", "PASS")
    assert (
        "DISK_GUARD overall=PASS exit_code=0 source=test-only" in result.stdout
    )


@pytest.mark.parametrize(
    ("c_free_gb", "d_free_gb", "warn_drive"),
    [
        (9.999, 25, "C"),
        (10, 24.999, "D"),
        (8, 20, "C"),
    ],
)
def test_warn_below_warning_thresholds_without_failing(
    c_free_gb: float, d_free_gb: float, warn_drive: str
) -> None:
    result = _run_guard(
        "-TestOnly",
        "-TestOnlyCFreeGb",
        c_free_gb,
        "-TestOnlyDFreeGb",
        d_free_gb,
    )

    assert result.returncode == 0, _output(result)
    _assert_drive_status(result, warn_drive, "WARN")
    assert (
        "DISK_GUARD overall=WARN exit_code=0 source=test-only" in result.stdout
    )


@pytest.mark.parametrize(
    ("c_free_gb", "d_free_gb", "failed_drive"),
    [
        (7.999, 25, "C"),
        (10, 19.999, "D"),
    ],
)
def test_fail_below_failure_thresholds(
    c_free_gb: float, d_free_gb: float, failed_drive: str
) -> None:
    result = _run_guard(
        "-TestOnly",
        "-TestOnlyCFreeGb",
        c_free_gb,
        "-TestOnlyDFreeGb",
        d_free_gb,
    )

    assert result.returncode == 2, _output(result)
    _assert_drive_status(result, failed_drive, "FAIL")
    assert (
        "DISK_GUARD overall=FAIL exit_code=2 source=test-only" in result.stdout
    )


@pytest.mark.parametrize(
    ("missing_drive", "observed_argument"),
    [
        ("C", "-TestOnlyDFreeGb"),
        ("D", "-TestOnlyCFreeGb"),
    ],
)
def test_missing_drive_is_a_safe_failure(
    missing_drive: str, observed_argument: str
) -> None:
    result = _run_guard(
        "-TestOnly",
        "-TestOnlyMissingDrive",
        missing_drive,
        observed_argument,
        100,
    )

    assert result.returncode == 2, _output(result)
    assert re.search(
        rf"(?m)^DISK_GUARD drive={missing_drive} free_gb=unavailable .* "
        r"status=FAIL source=test-only reason=drive-not-found$",
        result.stdout,
    ), _output(result)
    assert (
        "DISK_GUARD overall=FAIL exit_code=2 source=test-only" in result.stdout
    )


def test_synthetic_values_cannot_bypass_default_checks_silently() -> None:
    result = _run_guard(
        "-TestOnlyCFreeGb",
        100,
        "-TestOnlyDFreeGb",
        100,
    )

    assert result.returncode == 2, _output(result)
    assert "reason=test-only-switch-required" in result.stdout
    assert "source=actual" not in result.stdout


def test_test_only_mode_requires_a_complete_observation() -> None:
    result = _run_guard("-TestOnly", "-TestOnlyCFreeGb", 100)

    assert result.returncode == 2, _output(result)
    assert "reason=missing-test-observation-D" in result.stdout


def test_default_execution_reports_both_actual_drives() -> None:
    result = _run_guard()

    assert result.returncode in {0, 2}, _output(result)
    assert re.search(
        r"(?m)^DISK_GUARD drive=C .* source=actual(?: reason=[\w-]+)?$",
        result.stdout,
    ), _output(result)
    assert re.search(
        r"(?m)^DISK_GUARD drive=D .* source=actual(?: reason=[\w-]+)?$",
        result.stdout,
    ), _output(result)
    assert re.search(
        rf"(?m)^DISK_GUARD overall=(?:PASS|WARN|FAIL) "
        rf"exit_code={result.returncode} source=actual$",
        result.stdout,
    ), _output(result)
