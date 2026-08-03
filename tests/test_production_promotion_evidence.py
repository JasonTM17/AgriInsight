from __future__ import annotations

import json
import hashlib
import os
import shutil
import subprocess
from collections.abc import Callable
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Any

import pytest


ROOT = Path(__file__).parents[1]
VALIDATOR = ROOT / "scripts" / "validate-production-promotion-evidence.ps1"
RELEASE_WRAPPER = ROOT / "scripts" / "start-production-release-compose.ps1"
POWERSHELL = shutil.which("pwsh") or shutil.which("powershell")


def _digest_image(name: str, digest_character: str) -> str:
    return f"ghcr.io/jasontm17/agriinsight-{name}@sha256:{digest_character * 64}"


def _timestamp(offset: timedelta) -> str:
    value = datetime.now(timezone.utc) + offset
    return value.isoformat().replace("+00:00", "Z")


def _endpoint_sha256(endpoint: str) -> str:
    return hashlib.sha256(endpoint.encode("utf-8")).hexdigest()


def _approval(name: str) -> dict[str, str]:
    return {
        "control": name,
        "owner": f"{name}-owner",
        "approval_ref": f"https://approvals.example.invalid/{name}",
        "approved_at_utc": _timestamp(timedelta(hours=-1)),
        "due_at_utc": _timestamp(timedelta(days=1)),
        "unlock_criterion": f"{name} control approved",
        "rollback_responsibility": f"{name}-owner restores the prior approved state",
    }


def _valid_evidence() -> dict[str, Any]:
    return {
        "format_version": 3,
        "release": {
            "environment": "production",
            "tag": "v0.4.0",
            "commit": "616527dcc7f4a03720fb48e617f9310ab9614873",
            "ci_run_url": "https://github.com/JasonTM17/AgriInsight/actions/runs/30697294137",
            "publication_run_url": "https://github.com/JasonTM17/AgriInsight/actions/runs/30697808763",
        },
        "images": {
            "python": _digest_image("python", "a"),
            "backend": _digest_image("backend", "b"),
            "web": _digest_image("web", "c"),
            "analytics_api": _digest_image("analytics-api", "d"),
        },
        "target": {
            "docker_context": "production-control",
            "docker_endpoint_sha256": _endpoint_sha256(
                "ssh://production.example.invalid"
            ),
            "deployment_identity": "agriinsight-release",
        },
        "rollback": {
            "strategy": "redeploy-previous-digest",
            "authority": "release-owner",
            "evidence_ref": "https://approvals.example.invalid/rollback",
            "previous_release": {
                "environment": "production",
                "tag": "v0.3.1",
                "commit": "1111111111111111111111111111111111111111",
                "ci_run_url": "https://github.com/JasonTM17/AgriInsight/actions/runs/30600000001",
                "publication_run_url": "https://github.com/JasonTM17/AgriInsight/actions/runs/30600000002",
            },
            "previous_images": {
                "python": _digest_image("python", "e"),
                "backend": _digest_image("backend", "f"),
                "web": _digest_image("web", "1"),
                "analytics_api": _digest_image("analytics-api", "2"),
            },
        },
        "recovery": {
            "rpo": "1h",
            "rto": "4h",
            "retention": "35d",
            "encrypted_off_host_backup_ref": "https://approvals.example.invalid/backup-policy",
            "key_owner": "security-owner",
            "restore_owner": "database-owner",
            "timed_drill_ref": "https://approvals.example.invalid/restore-drill",
        },
        "approvals": {
            name: _approval(name)
            for name in (
                "oidc",
                "broker",
                "hosting",
                "deployment",
                "recovery",
                "audit_retention",
                "credential_rotation",
                "observability",
                "registry",
                "license",
            )
        },
    }


def _write_evidence(tmp_path: Path, evidence: dict[str, Any]) -> Path:
    path = tmp_path / "production-promotion-evidence.json"
    path.write_text(json.dumps(evidence), encoding="utf-8")
    return path


def _environment_for(evidence: dict[str, Any]) -> dict[str, str]:
    environment = os.environ.copy()
    environment.update(
        {
            "AGRIINSIGHT_PYTHON_IMAGE": evidence["images"]["python"],
            "AGRIINSIGHT_BACKEND_IMAGE": evidence["images"]["backend"],
            "AGRIINSIGHT_WEB_IMAGE": evidence["images"]["web"],
            "AGRIINSIGHT_ANALYTICS_API_IMAGE": evidence["images"][
                "analytics_api"
            ],
            "AGRIINSIGHT_PRODUCTION_DOCKER_CONTEXT": evidence["target"][
                "docker_context"
            ],
        }
    )
    return environment


@pytest.fixture
def run_script(
    tmp_path: Path,
) -> Callable[
    [Path, dict[str, Any], dict[str, str] | None], subprocess.CompletedProcess[str]
]:
    if POWERSHELL is None:
        pytest.skip("PowerShell is required for the deployment preflight test")

    def run(
        script: Path,
        evidence: dict[str, Any],
        environment: dict[str, str] | None = None,
    ) -> subprocess.CompletedProcess[str]:
        return subprocess.run(
            [
                POWERSHELL,
                "-NoProfile",
                "-ExecutionPolicy",
                "Bypass",
                "-File",
                str(script),
                "-EvidenceFile",
                str(_write_evidence(tmp_path, evidence)),
            ],
            cwd=ROOT,
            check=False,
            text=True,
            capture_output=True,
            env=environment or _environment_for(evidence),
        )

    return run


def test_valid_production_evidence_matches_the_selected_image_digests(
    run_script: Callable[
        [Path, dict[str, Any], dict[str, str] | None], subprocess.CompletedProcess[str]
    ],
) -> None:
    result = run_script(VALIDATOR, _valid_evidence())

    assert result.returncode == 0, result.stderr
    assert "PRODUCTION_PROMOTION_EVIDENCE status=PASS" in result.stdout
    assert "release=v0.4.0" in result.stdout
    assert "image_count=4" in result.stdout


def test_legacy_format_version_fails_closed(
    run_script: Callable[
        [Path, dict[str, Any], dict[str, str] | None], subprocess.CompletedProcess[str]
    ],
) -> None:
    evidence = _valid_evidence()
    evidence["format_version"] = 2

    result = run_script(VALIDATOR, evidence)

    assert result.returncode != 0
    assert "format_version must equal the integer 3." in result.stderr
    assert "status=PASS" not in result.stdout


@pytest.mark.parametrize("format_version", ("3", 3.0, "03"))
def test_format_version_requires_an_exact_integer_json_value(
    run_script: Callable[
        [Path, dict[str, Any], dict[str, str] | None], subprocess.CompletedProcess[str]
    ],
    format_version: str | float,
) -> None:
    evidence = _valid_evidence()
    evidence["format_version"] = format_version

    result = run_script(VALIDATOR, evidence)

    assert result.returncode != 0
    assert "format_version must equal the integer 3." in result.stderr
    assert "status=PASS" not in result.stdout


def test_approval_controls_are_exact_and_match_the_v3_template(
    run_script: Callable[
        [Path, dict[str, Any], dict[str, str] | None], subprocess.CompletedProcess[str]
    ],
) -> None:
    evidence = _valid_evidence()
    template = json.loads(
        (ROOT / "deploy" / "production-promotion-evidence.template.json").read_text(
            encoding="utf-8"
        )
    )

    assert template["format_version"] == evidence["format_version"] == 3
    assert set(template["approvals"]) == set(evidence["approvals"])
    assert all(
        row["control"] == name and row["rollback_responsibility"] == "REQUIRED"
        for name, row in template["approvals"].items()
    )

    evidence["approvals"]["unknown_control"] = _approval("unknown_control")
    result = run_script(VALIDATOR, evidence)

    assert result.returncode != 0
    assert "approvals must contain exactly the supported controls." in result.stderr
    assert "status=PASS" not in result.stdout

    evidence = _valid_evidence()
    evidence["approvals"]["broker"]["access_token"] = "must-not-be-accepted"
    result = run_script(VALIDATOR, evidence)

    assert result.returncode != 0
    assert "approvals.broker must contain exactly the supported fields." in result.stderr
    assert "access_token" not in result.stderr
    assert "must-not-be-accepted" not in result.stderr
    assert "status=PASS" not in result.stdout


@pytest.mark.parametrize(
    "owner",
    (
        "UNASSIGNED — NO-GO",
        "UNASSIGNED – NO-GO",
        "UNASSIGNED ‑ NO-GO",
        "UNASSIGNED – NO–GO",
        "NO–GO",
        "UNASSIGNED − NO-GO",
        "UNASSIGNED\u200b—\u200bNO-GO",
        "UNASSIGNED-NO-GO",
    ),
)
def test_unresolved_owner_rejects_unicode_dash_confusables(
    run_script: Callable[
        [Path, dict[str, Any], dict[str, str] | None], subprocess.CompletedProcess[str]
    ],
    owner: str,
) -> None:
    evidence = _valid_evidence()
    evidence["approvals"]["broker"]["owner"] = owner

    result = run_script(VALIDATOR, evidence)

    assert result.returncode != 0
    assert "approvals.broker.owner" in result.stderr
    assert owner not in result.stderr
    assert "status=PASS" not in result.stdout


@pytest.mark.parametrize(
    "owner",
    ("Owner pending review", "No-Go Logistics", "unknown-farm-owner"),
)
def test_approval_marker_check_does_not_reject_embedded_owner_words(
    run_script: Callable[
        [Path, dict[str, Any], dict[str, str] | None], subprocess.CompletedProcess[str]
    ],
    owner: str,
) -> None:
    evidence = _valid_evidence()
    evidence["approvals"]["broker"]["owner"] = owner

    result = run_script(VALIDATOR, evidence)

    assert result.returncode == 0, result.stderr
    assert "PRODUCTION_PROMOTION_EVIDENCE status=PASS" in result.stdout


@pytest.mark.parametrize(
    "mutate",
    [
        lambda evidence: evidence["images"].update(
            {"web": "ghcr.io/jasontm17/agriinsight-web:0.4.0"}
        ),
        lambda evidence: evidence["approvals"]["broker"].update({"owner": "REQUIRED"}),
        lambda evidence: evidence["release"].update({"commit": "not-a-commit"}),
        lambda evidence: evidence["release"].update(
            {"ci_run_url": "https://example.invalid/actions/runs/1"}
        ),
    ],
)
def test_incomplete_or_mutable_promotion_evidence_fails_closed(
    run_script: Callable[
        [Path, dict[str, Any], dict[str, str] | None], subprocess.CompletedProcess[str]
    ],
    mutate: Callable[[dict[str, Any]], None],
) -> None:
    evidence = _valid_evidence()
    mutate(evidence)

    result = run_script(VALIDATOR, evidence)

    assert result.returncode != 0
    assert "status=PASS" not in result.stdout
    assert "REQUIRED" not in result.stderr


@pytest.mark.parametrize(
    ("mutate", "expected_error"),
    [
        (
            lambda evidence: evidence["approvals"]["broker"].update(
                {"owner": "UNASSIGNED — NO-GO"}
            ),
            "approvals.broker.owner",
        ),
        (
            lambda evidence: evidence["approvals"]["broker"].update(
                {"rollback_responsibility": "TBD"}
            ),
            "approvals.broker.rollback_responsibility",
        ),
        (
            lambda evidence: evidence["approvals"]["broker"].pop(
                "rollback_responsibility"
            ),
            "approvals.broker.rollback_responsibility",
        ),
    ],
)
def test_unresolved_owner_or_control_rollback_fails_closed(
    run_script: Callable[
        [Path, dict[str, Any], dict[str, str] | None], subprocess.CompletedProcess[str]
    ],
    mutate: Callable[[dict[str, Any]], object],
    expected_error: str,
) -> None:
    evidence = _valid_evidence()
    mutate(evidence)

    result = run_script(VALIDATOR, evidence)

    assert result.returncode != 0
    assert "status=PASS" not in result.stdout
    assert expected_error in result.stderr


def test_environment_digest_mismatch_or_missing_value_fails_closed(
    run_script: Callable[
        [Path, dict[str, Any], dict[str, str] | None], subprocess.CompletedProcess[str]
    ],
) -> None:
    evidence = _valid_evidence()
    environment = _environment_for(evidence)
    mismatched_image = _digest_image("web", "d")
    environment["AGRIINSIGHT_WEB_IMAGE"] = mismatched_image

    mismatch = run_script(VALIDATOR, evidence, environment)
    assert mismatch.returncode != 0
    assert "AGRIINSIGHT_WEB_IMAGE does not match promotion evidence." in mismatch.stderr
    assert mismatched_image not in mismatch.stderr

    environment.pop("AGRIINSIGHT_WEB_IMAGE")
    missing = run_script(VALIDATOR, evidence, environment)
    assert missing.returncode != 0
    assert "AGRIINSIGHT_WEB_IMAGE is required." in missing.stderr


def test_promotion_evidence_binds_a_non_placeholder_docker_target(
    run_script: Callable[
        [Path, dict[str, Any], dict[str, str] | None], subprocess.CompletedProcess[str]
    ],
) -> None:
    evidence = _valid_evidence()
    environment = _environment_for(evidence)
    environment["AGRIINSIGHT_PRODUCTION_DOCKER_CONTEXT"] = "wrong-target"

    mismatch = run_script(VALIDATOR, evidence, environment)

    assert mismatch.returncode != 0
    assert "AGRIINSIGHT_PRODUCTION_DOCKER_CONTEXT does not match promotion evidence." in mismatch.stderr
    assert "status=PASS" not in mismatch.stdout

    evidence["target"]["docker_context"] = "REQUIRED"
    placeholder = run_script(VALIDATOR, evidence, _environment_for(evidence))

    assert placeholder.returncode != 0
    assert "target.docker_context" in placeholder.stderr

    evidence = _valid_evidence()
    evidence["target"]["deployment_identity"] = "wrong-project"
    wrong_project = run_script(VALIDATOR, evidence, _environment_for(evidence))

    assert wrong_project.returncode != 0
    assert "target.deployment_identity must equal agriinsight-release." in wrong_project.stderr

    evidence = _valid_evidence()
    evidence["target"]["docker_endpoint_sha256"] = "REQUIRED"
    missing_endpoint_identity = run_script(VALIDATOR, evidence, _environment_for(evidence))

    assert missing_endpoint_identity.returncode != 0
    assert "target.docker_endpoint_sha256" in missing_endpoint_identity.stderr


def test_evidence_rejects_untrusted_or_wrong_component_image_repositories(
    run_script: Callable[
        [Path, dict[str, Any], dict[str, str] | None], subprocess.CompletedProcess[str]
    ],
) -> None:
    untrusted = _valid_evidence()
    untrusted["images"]["backend"] = (
        "registry.example.invalid/agriinsight-backend@sha256:" + "a" * 64
    )
    result = run_script(VALIDATOR, untrusted, _environment_for(untrusted))
    assert result.returncode != 0
    assert "approved first-party backend repository" in result.stderr

    wrong_kind = _valid_evidence()
    wrong_kind["images"]["web"] = wrong_kind["images"]["backend"]
    result = run_script(VALIDATOR, wrong_kind, _environment_for(wrong_kind))
    assert result.returncode != 0
    assert "approved first-party web repository" in result.stderr


@pytest.mark.parametrize(
    "mutate",
    [
        lambda evidence: evidence["approvals"]["broker"].update(
            {"due_at_utc": _timestamp(timedelta(minutes=-1))}
        ),
        lambda evidence: evidence["approvals"]["broker"].update(
            {
                "approved_at_utc": _timestamp(timedelta(hours=-1)),
                "due_at_utc": _timestamp(timedelta(hours=-2)),
            }
        ),
        lambda evidence: evidence["approvals"]["broker"].update(
            {"due_at_utc": "2030-01-01T00:00:00"}
        ),
        lambda evidence: evidence["approvals"]["broker"].update(
            {"approval_ref": evidence["approvals"]["oidc"]["approval_ref"]}
        ),
        lambda evidence: evidence["approvals"]["broker"].update(
            {"control": "oidc"}
        ),
    ],
)
def test_expired_or_ambiguous_approval_evidence_fails_closed(
    run_script: Callable[
        [Path, dict[str, Any], dict[str, str] | None], subprocess.CompletedProcess[str]
    ],
    mutate: Callable[[dict[str, Any]], None],
) -> None:
    evidence = _valid_evidence()
    mutate(evidence)

    result = run_script(VALIDATOR, evidence)

    assert result.returncode != 0
    assert "status=PASS" not in result.stdout


@pytest.mark.parametrize("replacement", ('"APPROVED_AT_UTC"', r'"approved\u005fat_utc"'))
def test_approval_timestamp_keys_must_be_canonical_and_utc_encoded(
    tmp_path: Path,
    replacement: str,
) -> None:
    if POWERSHELL is None:
        pytest.skip("PowerShell is required for the deployment preflight test")

    evidence = _valid_evidence()
    evidence_file = tmp_path / "production-promotion-evidence.json"
    evidence_file.write_text(
        json.dumps(evidence).replace('"approved_at_utc"', replacement, 1),
        encoding="utf-8",
    )
    result = subprocess.run(
        [
            POWERSHELL,
            "-NoProfile",
            "-ExecutionPolicy",
            "Bypass",
            "-File",
            str(VALIDATOR),
            "-EvidenceFile",
            str(evidence_file),
        ],
        cwd=ROOT,
        check=False,
        text=True,
        capture_output=True,
        env=_environment_for(evidence),
    )

    assert result.returncode != 0
    assert "status=PASS" not in result.stdout


@pytest.mark.parametrize(
    "mutate",
    [
        lambda evidence: evidence["rollback"]["previous_images"].pop("web"),
        lambda evidence: evidence["rollback"].pop("previous_release"),
        lambda evidence: evidence["rollback"].update(
            {"previous_release": evidence["release"].copy()}
        ),
        lambda evidence: evidence["rollback"].update(
            {
                "strategy": "disable-exposure",
                "disable_exposure_ref": "REQUIRED",
            }
        ),
    ],
)
def test_rollback_evidence_must_be_executable_or_explicit(
    run_script: Callable[
        [Path, dict[str, Any], dict[str, str] | None], subprocess.CompletedProcess[str]
    ],
    mutate: Callable[[dict[str, Any]], None],
) -> None:
    evidence = _valid_evidence()
    mutate(evidence)

    result = run_script(VALIDATOR, evidence)

    assert result.returncode != 0
    assert "status=PASS" not in result.stdout


def test_release_wrapper_rejects_invalid_evidence_before_docker_use(
    run_script: Callable[
        [Path, dict[str, Any], dict[str, str] | None], subprocess.CompletedProcess[str]
    ],
) -> None:
    evidence = _valid_evidence()
    evidence["images"]["web"] = "ghcr.io/jasontm17/agriinsight-web:0.4.0"

    result = run_script(RELEASE_WRAPPER, evidence, _environment_for(evidence))

    assert result.returncode != 0
    assert "images.web must be an immutable OCI digest reference." in result.stderr
    assert "Could not pull an approved release image." not in result.stderr


def test_failed_ci_metadata_stops_wrapper_before_docker_and_pins_github_host(
    tmp_path: Path,
) -> None:
    if POWERSHELL is None:
        pytest.skip("PowerShell is required for the deployment preflight test")

    evidence = _valid_evidence()
    evidence_file = _write_evidence(tmp_path, evidence)
    failed_run = json.dumps(
        {
            "path": ".github/workflows/ci.yml",
            "head_sha": evidence["release"]["commit"],
            "head_branch": "main",
            "event": "push",
            "status": "completed",
            "conclusion": "failure",
        }
    )
    command = "\n".join(
        (
            "function gh {",
            "  param([Parameter(ValueFromRemainingArguments = $true)] [string[]] $Arguments)",
            "  if (($Arguments -join ' ') -notmatch '--hostname github\\.com') {",
            "    throw 'GH_HOST_UNPINNED'",
            "  }",
            f"  '{failed_run}'",
            "}",
            "function docker {",
            "  param([Parameter(ValueFromRemainingArguments = $true)] [string[]] $Arguments)",
            "  if (($Arguments -join ' ') -eq 'context show') { 'production-control'; return }",
            "  throw 'DOCKER_SHOULD_NOT_RUN'",
            "}",
            f"& '{RELEASE_WRAPPER}' -EvidenceFile '{evidence_file}'",
        )
    )
    environment = _environment_for(evidence)
    environment["GH_HOST"] = "evil.example.invalid"

    result = subprocess.run(
        [POWERSHELL, "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", command],
        cwd=ROOT,
        check=False,
        text=True,
        capture_output=True,
        env=environment,
    )

    assert result.returncode != 0
    assert "CI workflow metadata does not match the approved release." in result.stderr
    assert "DOCKER_SHOULD_NOT_RUN" not in result.stderr
    assert "GH_HOST_UNPINNED" not in result.stderr


def test_rollback_mode_requires_explicit_production_change_confirmation(
    tmp_path: Path,
) -> None:
    if POWERSHELL is None:
        pytest.skip("PowerShell is required for the deployment preflight test")

    evidence = _valid_evidence()
    result = subprocess.run(
        [
            POWERSHELL,
            "-NoProfile",
            "-ExecutionPolicy",
            "Bypass",
            "-File",
            str(RELEASE_WRAPPER),
            "-EvidenceFile",
            str(_write_evidence(tmp_path, evidence)),
            "-Mode",
            "Rollback",
        ],
        cwd=ROOT,
        check=False,
        text=True,
        capture_output=True,
        env=_environment_for(evidence),
    )

    assert result.returncode != 0
    assert "Deploy and Rollback modes require -ConfirmProductionChange." in result.stderr
    assert "Could not pull an approved release image." not in result.stderr


def test_disable_exposure_skips_github_and_registry_calls_after_local_target_validation(
    tmp_path: Path,
) -> None:
    if POWERSHELL is None:
        pytest.skip("PowerShell is required for the deployment preflight test")

    evidence = _valid_evidence()
    evidence["rollback"].update(
        {
            "strategy": "disable-exposure",
            "disable_exposure_ref": "https://approvals.example.invalid/disable-exposure",
        }
    )
    evidence_file = _write_evidence(tmp_path, evidence)
    command = "\n".join(
        (
            "$global:agriInsightDockerCalls = @()",
            "$global:agriInsightReleaseDisabled = $false",
            "function docker {",
            "  param([Parameter(ValueFromRemainingArguments = $true)] [string[]] $Arguments)",
            "  $global:agriInsightDockerCalls += ($Arguments -join ' ')",
            "  $joined = $Arguments -join ' '",
            "  if ($joined -eq 'context show') { 'production-control'; return }",
            "  if ($joined -eq 'context inspect production-control --format {{.Endpoints.docker.Host}}') { 'ssh://production.example.invalid'; return }",
            "  if ($joined -match 'compose .* ps --all --quiet$') { if (-not $global:agriInsightReleaseDisabled) { 'release-container' }; return }",
            "  if ($joined -match 'compose .* down') { $global:agriInsightReleaseDisabled = $true; return }",
            "  throw 'UNEXPECTED_DOCKER_CALL'",
            "}",
            "function gh { throw 'GH_MUST_NOT_RUN_FOR_DISABLE_EXPOSURE' }",
            f"& '{RELEASE_WRAPPER}' -EvidenceFile '{evidence_file}' -Mode Rollback -ConfirmProductionChange",
            "if (($global:agriInsightDockerCalls -join '|') -match 'pull|imagetools|config') { throw 'REGISTRY_OR_CONFIG_CALL' }",
        )
    )

    result = subprocess.run(
        [POWERSHELL, "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", command],
        cwd=ROOT,
        check=False,
        text=True,
        capture_output=True,
        env=_environment_for(evidence),
    )

    assert result.returncode == 0, result.stderr
    assert "PRODUCTION_RELEASE_COMPOSE status=PASS mode=Rollback" in result.stdout
    assert "GH_MUST_NOT_RUN_FOR_DISABLE_EXPOSURE" not in result.stderr


def test_disable_exposure_reports_already_disabled_without_claiming_a_new_shutdown(
    tmp_path: Path,
) -> None:
    if POWERSHELL is None:
        pytest.skip("PowerShell is required for the deployment preflight test")

    evidence = _valid_evidence()
    evidence["rollback"].update(
        {
            "strategy": "disable-exposure",
            "disable_exposure_ref": "https://approvals.example.invalid/disable-exposure",
        }
    )
    evidence_file = _write_evidence(tmp_path, evidence)
    command = "\n".join(
        (
            "function docker {",
            "  param([Parameter(ValueFromRemainingArguments = $true)] [string[]] $Arguments)",
            "  $joined = $Arguments -join ' '",
            "  if ($joined -eq 'context show') { 'production-control'; return }",
            "  if ($joined -eq 'context inspect production-control --format {{.Endpoints.docker.Host}}') { 'ssh://production.example.invalid'; return }",
            "  if ($joined -match 'compose .* ps --all --quiet$') { return }",
            "  throw 'UNEXPECTED_DOCKER_CALL'",
            "}",
            "function gh { throw 'GH_MUST_NOT_RUN_FOR_DISABLE_EXPOSURE' }",
            f"& '{RELEASE_WRAPPER}' -EvidenceFile '{evidence_file}' -Mode Rollback -ConfirmProductionChange",
        )
    )

    result = subprocess.run(
        [POWERSHELL, "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", command],
        cwd=ROOT,
        check=False,
        text=True,
        capture_output=True,
        env=_environment_for(evidence),
    )

    assert result.returncode == 0, result.stderr
    assert "PRODUCTION_RELEASE_COMPOSE status=ALREADY_DISABLED mode=Rollback" in result.stdout
    assert "status=PASS" not in result.stdout


def test_release_wrapper_binds_the_active_docker_context_and_checks_every_release_service() -> None:
    wrapper = RELEASE_WRAPPER.read_text(encoding="utf-8")

    assert "function Assert-DockerContext" in wrapper
    assert "docker context inspect" in wrapper
    assert "Docker context endpoint does not match promotion evidence." in wrapper
    assert "docker context show" in wrapper
    assert "Docker context does not match promotion evidence." in wrapper
    assert '"--project-name", $evidence.Target.DeploymentIdentity' in wrapper
    assert '"dashboard"' in wrapper
    assert "pipeline" in wrapper
    assert '@("ps", "--all", "--quiet", "pipeline")' in wrapper
    assert "Pipeline did not complete successfully." in wrapper
    assert (
        wrapper.index("Assert-ReleaseWorkflowEvidence -Release $evidence.Release")
        < wrapper.rindex("Assert-DockerContext -Target $evidence.Target")
    )


def test_compose_up_accepts_a_stopped_successful_pipeline_from_the_expected_project(
    tmp_path: Path,
) -> None:
    if POWERSHELL is None:
        pytest.skip("PowerShell is required for the deployment preflight test")

    command = "\n".join(
        (
            f"$source = Get-Content -LiteralPath '{RELEASE_WRAPPER}' -Raw",
            "$start = $source.IndexOf('function Test-LastCommandSucceeded')",
            "$end = $source.IndexOf('if ($Mode -in @')",
            "Invoke-Expression $source.Substring($start, $end - $start)",
            "$global:agriInsightDockerCalls = @()",
            "function docker {",
            "  param([Parameter(ValueFromRemainingArguments = $true)] [string[]] $Arguments)",
            "  $joined = $Arguments -join ' '",
            "  $global:agriInsightDockerCalls += $joined",
            "  if ($joined -eq 'compose up --detach --wait --wait-timeout 180') { return }",
            "  if ($joined -eq 'compose ps --status running --services') { 'dashboard'; 'backend'; 'analytics'; 'web'; return }",
            "  if ($joined -eq 'compose ps --all --quiet pipeline') { 'pipeline-container'; return }",
            "  if ($joined -eq 'inspect pipeline-container --format {{.State.Status}}/{{.State.ExitCode}}') { 'exited/0'; return }",
            "  throw \"UNEXPECTED_DOCKER_CALL=$joined\"",
            "}",
            "Invoke-ComposeUpAndVerify -ComposeArguments @('compose')",
            "if ($global:agriInsightDockerCalls -notcontains 'compose ps --all --quiet pipeline') { throw 'PIPELINE_ALL_NOT_REQUESTED' }",
        )
    )

    result = subprocess.run(
        [POWERSHELL, "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", command],
        cwd=ROOT,
        check=False,
        text=True,
        capture_output=True,
    )

    assert result.returncode == 0, result.stderr


@pytest.mark.parametrize(
    ("previous_tag", "previous_updated_at", "expected_error"),
    [
        (
            "v0.5.0",
            "2026-08-01T10:00:00Z",
            "Rollback prior release is not earlier than the current release.",
        ),
        (
            "v0.3.1",
            "2026-08-03T10:00:00Z",
            "Rollback prior release was not published before the current release.",
        ),
    ],
)
def test_rollback_rejects_a_future_or_newer_publication_before_docker(
    tmp_path: Path,
    previous_tag: str,
    previous_updated_at: str,
    expected_error: str,
) -> None:
    if POWERSHELL is None:
        pytest.skip("PowerShell is required for the deployment preflight test")

    evidence = _valid_evidence()
    previous_release = evidence["rollback"]["previous_release"]
    previous_release["tag"] = previous_tag
    previous_release["commit"] = "2222222222222222222222222222222222222222"
    evidence_file = _write_evidence(tmp_path, evidence)

    def workflow_run(
        release: dict[str, str],
        workflow_path: str,
        branch: str,
        created_at: str,
        updated_at: str,
    ) -> dict[str, str]:
        return {
            "path": workflow_path,
            "head_sha": release["commit"],
            "head_branch": branch,
            "event": "push",
            "status": "completed",
            "conclusion": "success",
            "created_at": created_at,
            "updated_at": updated_at,
        }

    current_release = evidence["release"]
    runs = (
        workflow_run(
            current_release,
            ".github/workflows/ci.yml",
            "main",
            "2026-08-02T09:00:00Z",
            "2026-08-02T09:10:00Z",
        ),
        workflow_run(
            current_release,
            ".github/workflows/publish-images.yml",
            current_release["tag"],
            "2026-08-02T09:30:00Z",
            "2026-08-02T10:00:00Z",
        ),
        workflow_run(
            previous_release,
            ".github/workflows/ci.yml",
            "main",
            "2026-08-01T09:00:00Z",
            "2026-08-01T09:10:00Z",
        ),
        workflow_run(
            previous_release,
            ".github/workflows/publish-images.yml",
            previous_release["tag"],
            "2026-08-01T10:00:00Z",
            previous_updated_at,
        ),
    )
    command = "\n".join(
        (
            "$global:agriInsightGhRuns = @(",
            *(f"  '{json.dumps(run)}'" for run in runs),
            ")",
            "$global:agriInsightGhCall = 0",
            "function gh {",
            "  param([Parameter(ValueFromRemainingArguments = $true)] [string[]] $Arguments)",
            "  $global:agriInsightGhCall += 1",
            "  $global:agriInsightGhRuns[($global:agriInsightGhCall - 1)]",
            "}",
            "function docker {",
            "  param([Parameter(ValueFromRemainingArguments = $true)] [string[]] $Arguments)",
            "  if (($Arguments -join ' ') -eq 'context show') { 'production-control'; return }",
            "  throw 'DOCKER_SHOULD_NOT_RUN'",
            "}",
            f"& '{RELEASE_WRAPPER}' -EvidenceFile '{evidence_file}' -Mode Rollback -ConfirmProductionChange",
        )
    )
    result = subprocess.run(
        [POWERSHELL, "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", command],
        cwd=ROOT,
        check=False,
        text=True,
        capture_output=True,
        env=_environment_for(evidence),
    )

    assert result.returncode != 0
    assert expected_error in result.stderr
    assert "DOCKER_SHOULD_NOT_RUN" not in result.stderr
