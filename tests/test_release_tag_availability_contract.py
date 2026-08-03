from __future__ import annotations

import os
import shlex
import stat
import subprocess
from pathlib import Path


PROJECT_ROOT = Path(__file__).resolve().parents[1]
TAG_CHECK = PROJECT_ROOT / "scripts" / "refuse-existing-release-tags.sh"


def _bash_path(path: Path) -> str:
    if os.name == "nt":
        return f"/mnt/{path.drive[0].lower()}{path.as_posix()[2:]}"
    return str(path)


def _run_tag_check(
    tmp_path: Path, docker_exit_code: int, docker_stderr: str
) -> subprocess.CompletedProcess[str]:
    fake_docker = tmp_path / "docker"
    fake_docker.write_text(
        "#!/usr/bin/env bash\n"
        f"printf '%s\\n' {docker_stderr!r} >&2\n"
        f"exit {docker_exit_code}\n",
        encoding="utf-8",
        newline="\n",
    )
    fake_docker.chmod(fake_docker.stat().st_mode | stat.S_IEXEC)
    environment = os.environ.copy()
    command_arguments = [
        "bash",
        _bash_path(TAG_CHECK),
        "0.4.1",
        "sha-0123456789abcdef0123456789abcdef01234567",
        "example.invalid/agriinsight-backend",
    ]
    if os.name == "nt":
        shell_command = (
            f"PATH={shlex.quote(_bash_path(tmp_path))}:/usr/bin:/bin; exec "
            + " ".join(shlex.quote(argument) for argument in command_arguments)
        )
        command_arguments = [
            "bash",
            "-lc",
            shell_command,
        ]
    else:
        environment["PATH"] = f"{tmp_path}{os.pathsep}{environment['PATH']}"

    return subprocess.run(
        command_arguments,
        cwd=PROJECT_ROOT,
        capture_output=True,
        check=False,
        env=environment,
        text=True,
        timeout=30,
    )


def test_tag_availability_accepts_only_an_explicit_missing_manifest(
    tmp_path: Path,
) -> None:
    result = _run_tag_check(
        tmp_path,
        docker_exit_code=1,
        docker_stderr="ERROR: manifest unknown: manifest unknown",
    )

    assert result.returncode == 0, result.stderr
    assert "Confirmed missing release tag" in result.stdout


def test_tag_availability_refuses_existing_tags(tmp_path: Path) -> None:
    result = _run_tag_check(tmp_path, docker_exit_code=0, docker_stderr="")

    assert result.returncode != 0
    assert "Refusing to overwrite existing release tag" in result.stderr


def test_tag_availability_fails_closed_when_registry_state_is_unknown(
    tmp_path: Path,
) -> None:
    result = _run_tag_check(
        tmp_path,
        docker_exit_code=1,
        docker_stderr="ERROR: dial tcp: registry is unavailable",
    )

    assert result.returncode != 0
    assert "Could not establish immutable-tag availability" in result.stderr


def test_publication_workflow_uses_the_fail_closed_tag_helper() -> None:
    workflow = (
        PROJECT_ROOT / ".github" / "workflows" / "publish-images.yml"
    ).read_text(encoding="utf-8")

    assert "bash scripts/refuse-existing-release-tags.sh" in workflow
    assert '"$VERSION" "sha-$' + '{GITHUB_SHA}"' in workflow
