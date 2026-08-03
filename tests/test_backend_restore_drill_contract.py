from __future__ import annotations

import base64
import hashlib
import json
import os
import shutil
import subprocess
import tempfile
import time
from pathlib import Path

import pytest


PROJECT_ROOT = Path(__file__).resolve().parents[1]
RESTORE_DRILL = PROJECT_ROOT / "scripts" / "run-backend-restore-drill.ps1"
RESTORE = PROJECT_ROOT / "scripts" / "restore-backend-postgres.ps1"
BACKUP = PROJECT_ROOT / "scripts" / "backup-backend-postgres.ps1"
BACKEND_RUNNER = PROJECT_ROOT / "scripts" / "run-backend-tests.ps1"
HOSTED_DRILL = PROJECT_ROOT / "scripts" / "run-hosted-backend-restore-drill.ps1"
HOSTED_CONTAINER_HELPERS = (
    PROJECT_ROOT / "scripts" / "hosted-recovery-container-helpers.psm1"
)
RECOVERY_RUNTIME_HELPERS = (
    PROJECT_ROOT / "scripts" / "recovery-runtime-helpers.psm1"
)
POWERSHELL = (
    shutil.which("powershell.exe")
    or shutil.which("powershell")
    or shutil.which("pwsh")
)


def _powershell_environment() -> dict[str, str]:
    environment = os.environ.copy()
    if os.name != "nt":
        environment["AGRIINSIGHT_RECOVERY_ALLOWED_ROOT"] = str(PROJECT_ROOT)
    return environment


def _run_drill(
    backup_file: Path, *arguments: str
) -> subprocess.CompletedProcess[str]:
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
            str(RESTORE_DRILL),
            "-BackupFile",
            str(backup_file),
            *arguments,
        ],
        cwd=PROJECT_ROOT,
        env=_powershell_environment(),
        capture_output=True,
        check=False,
        text=True,
        timeout=30,
    )


def _run_restore(
    backup_file: Path, *arguments: str
) -> subprocess.CompletedProcess[str]:
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
            str(RESTORE),
            "-BackupFile",
            str(backup_file),
            "-RestoreDrillScope",
            "local-or-staging",
            *arguments,
        ],
        cwd=PROJECT_ROOT,
        env=_powershell_environment(),
        capture_output=True,
        check=False,
        text=True,
        timeout=30,
    )


def _run_powershell(command: str) -> subprocess.CompletedProcess[str]:
    if POWERSHELL is None:
        pytest.skip("PowerShell is not available")

    encoded_command = base64.b64encode(command.encode("utf-16-le")).decode()
    return subprocess.run(
        [
            POWERSHELL,
            "-NoLogo",
            "-NoProfile",
            "-NonInteractive",
            "-EncodedCommand",
            encoded_command,
        ],
        cwd=PROJECT_ROOT,
        env=_powershell_environment(),
        capture_output=True,
        check=False,
        text=True,
        timeout=30,
    )


def _start_powershell(command: str) -> subprocess.Popen[str]:
    if POWERSHELL is None:
        pytest.skip("PowerShell is not available")

    encoded_command = base64.b64encode(command.encode("utf-16-le")).decode()
    return subprocess.Popen(
        [
            POWERSHELL,
            "-NoLogo",
            "-NoProfile",
            "-NonInteractive",
            "-EncodedCommand",
            encoded_command,
        ],
        cwd=PROJECT_ROOT,
        env=_powershell_environment(),
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
    )


def _powershell_literal(path: Path) -> str:
    return str(path).replace("'", "''")


def _write_backup_fixture(schema_version: int) -> tuple[Path, Path]:
    directory = Path(
        tempfile.mkdtemp(prefix="restore-drill-contract-", dir=PROJECT_ROOT)
    )
    backup_file = directory / "current.dump"
    backup_file.write_bytes(b"checksum-verified-non-production-fixture")
    backup_hash = hashlib.sha256(backup_file.read_bytes()).hexdigest()
    backup_file.with_name(f"{backup_file.name}.metadata.json").write_text(
        json.dumps(
            {
                "format_version": 1,
                "flyway_schema_version": str(schema_version),
                "sha256": backup_hash,
            }
        ),
        encoding="utf-8",
    )
    return directory, backup_file


def test_restore_records_the_restored_schema_version_without_destructive_flags() -> None:
    restore = RESTORE.read_text(encoding="utf-8")

    assert "[ValidateRange(30, 10000)]" in restore
    assert "[int]$MinimumSchemaVersion = 30" in restore
    assert "Source backup metadata schema is below the non-lowerable V30 minimum." in restore
    assert "restoredFlywaySchemaVersion = Invoke-Psql" in restore
    assert "restored_flyway_schema_version = $restoredFlywaySchemaVersion" in restore
    assert "Target database must be pre-created and empty" in restore
    assert "AGRIINSIGHT_RESTORE_DRILL_TARGET_DATABASE" in restore
    assert "agriinsight_restore_" in restore
    assert "AGRIINSIGHT_RESTORE_DRILL_HOST" in restore
    assert "AGRIINSIGHT_RESTORE_DRILL_PORT" in restore
    assert "AGRIINSIGHT_RESTORE_DRILL_ALLOWED_HOSTS" in restore
    assert "AGRIINSIGHT_RESTORE_DRILL_HOST is not in AGRIINSIGHT_RESTORE_DRILL_ALLOWED_HOSTS." in restore
    assert "must be the literal IPv4 loopback address 127.0.0.1 until a remote TLS provider contract is approved." in restore
    assert "Open-ExclusiveRestoreTargetMutex" in restore
    assert "AGRIINSIGHT_DB_HOST" not in restore
    assert "AGRIINSIGHT_DB_PORT" not in restore
    assert "Restore target must differ from the backup source database." in restore
    assert "Connected database does not match AGRIINSIGHT_RESTORE_DRILL_TARGET_DATABASE." in restore
    assert "restore_drill_scope = $RestoreDrillScope" in restore
    assert "pg_catalog.pg_proc" in restore
    assert "pg_catalog.pg_extension" in restore
    assert "Open-ReadLockedFileStream -Path $source" in restore
    assert "--single-transaction" in restore
    assert "--no-owner" in restore
    assert "--clean" not in restore.lower()
    assert "pg_restore failed; the target is retained for diagnosis." in restore
    assert "Dedicated restore-drill cluster contains another non-system database." in restore
    assert "pg_catalog.pg_database" in restore
    assert "pg_catalog.pg_stat_activity" in restore
    assert "Runtime tenant RLS smoke failed after restore." in restore
    assert "SELECT count(*) = 0 FROM tenants;" in restore
    assert "SELECT count(*) = 1" in restore
    assert "psql_version" in restore
    assert "pg_restore_version" in restore
    assert "source_backup_metadata" in restore


def test_backup_restore_and_drill_share_a_cmdlet_independent_sha256_helper() -> None:
    helper = (
        PROJECT_ROOT / "scripts" / "postgres-backup-integrity-helpers.psm1"
    ).read_text(encoding="utf-8")

    assert "function Get-Sha256Hex" in helper
    assert "function Open-ReadLockedFileStream" in helper
    assert "function Assert-DDrivePathWithoutReparsePoints" in helper
    assert "function Open-ExclusiveRestoreTargetMutex" in helper
    assert "function New-AdjacentTemporaryPath" in helper
    assert "function Publish-NewFile" in helper
    assert "[System.Security.Cryptography.SHA256]::Create()" in helper
    assert "[System.IO.FileShare]::Read" in helper
    assert "[System.IO.FileAttributes]::ReparsePoint" in helper
    assert "AGRIINSIGHT_RECOVERY_ALLOWED_ROOT" in helper
    assert "[System.Threading.Mutex]::new" in helper
    assert '"Global\\AgriInsightRestoreDrill_$identityHash"' in helper
    assert "$mutex.WaitOne(0)" in helper
    assert '[System.IO.Path]::GetPathRoot($resolved) -ine $approvedRoot' in helper
    assert '$resolved.StartsWith($approvedPrefix, [System.StringComparison]::Ordinal)' in helper
    assert "[System.IO.File]::Move($TemporaryPath, $Destination)" in helper
    assert "$sha256.Dispose()" in helper
    assert "$stream.Dispose()" in helper

    for script_name in (
        "backup-backend-postgres.ps1",
        "restore-backend-postgres.ps1",
        "run-backend-restore-drill.ps1",
    ):
        source = (PROJECT_ROOT / "scripts" / script_name).read_text(
            encoding="utf-8"
        )
        assert "postgres-backup-integrity-helpers.psm1" in source
        assert "Get-Sha256Hex" in source
        assert "Assert-DDrivePathWithoutReparsePoints" in source
        assert "Get-FileHash" not in source


def test_restore_drill_validates_current_schema_before_it_can_be_run() -> None:
    drill = (
        PROJECT_ROOT / "scripts" / "run-backend-restore-drill.ps1"
    ).read_text(encoding="utf-8")

    assert '[ValidateSet("Validate", "Run")]' in drill
    assert "[ValidateRange(30, 10000)]" in drill
    assert "[int] $MinimumSchemaVersion = 30" in drill
    assert "Run mode requires -ConfirmRestoreDrill." in drill
    assert "Run mode requires -RestoreDrillScope local-or-staging." in drill
    assert '"-RestoreDrillScope", $RestoreDrillScope' in drill
    assert 'Join-Path $PSScriptRoot "postgres-backup-integrity-helpers.psm1"' in drill
    assert "Get-Sha256Hex -Path $source" in drill
    assert "Source backup metadata schema is below the requested minimum." in drill
    assert "Restore report schema is below the requested minimum." in drill
    assert 'Join-Path $PSScriptRoot "restore-backend-postgres.ps1"' in drill
    assert 'restored_flyway_schema_version' in drill
    assert 'role_and_rls_gate' in drill
    assert 'RESTORE_DRILL status=PASS mode=$Mode scope=local-or-staging' in drill


def test_recovery_wrappers_require_explicit_hosted_mode_without_weakening_local_guards() -> None:
    helper = RECOVERY_RUNTIME_HELPERS.read_text(encoding="utf-8")
    backup = BACKUP.read_text(encoding="utf-8")
    restore = RESTORE.read_text(encoding="utf-8")
    drill = RESTORE_DRILL.read_text(encoding="utf-8")
    backend_runner = BACKEND_RUNNER.read_text(encoding="utf-8")

    assert "function Assert-GitHubHostedRecoveryRunner" in helper
    assert 'RUNNER_ENVIRONMENT -cne "github-hosted"' in helper
    assert 'RUNNER_OS -cne "Linux"' in helper
    assert "check-hosted-ci-disk.ps1" in helper
    assert "check-workspace-disk.ps1" in helper

    for source in (backup, restore, drill, backend_runner):
        assert "[switch]$HostedCi" in source or "[switch] $HostedCi" in source

    assert "Invoke-RecoveryDiskGuard" in backup
    assert "Invoke-RecoveryDiskGuard" in restore
    assert '"--quiet", "--set=ON_ERROR_STOP=1"' in restore
    assert "Invoke-RecoveryDiskGuard" in backend_runner
    assert 'if ($HostedCi) { $restoreArguments += "-HostedCi" }' in drill
    assert 'if ($HostedCi) { $backendArguments += "-HostedCi" }' in restore
    assert 'if ($isWindowsHost) { "mvnw.cmd" } else { "mvnw" }' in backend_runner
    assert '"-Dflyway.postgresql.transactional.lock=false"' in backend_runner
    pom = (PROJECT_ROOT / "backend" / "pom.xml").read_text(encoding="utf-8")
    assert "postgresqlTransactionalLock" not in pom


def test_hosted_restore_harness_uses_two_owned_postgres_18_clusters_and_safe_evidence() -> None:
    harness = HOSTED_DRILL.read_text(encoding="utf-8")
    container_helpers = HOSTED_CONTAINER_HELPERS.read_text(encoding="utf-8")
    implementation = harness + container_helpers

    assert "Assert-GitHubHostedRecoveryRunner" in harness
    assert "postgres:18.0-alpine@sha256:48c8ad3a7284b82be4482a52076d47d879fd6fb084a1cbfccbd551f9331b0e40" in harness
    assert 'sourceDatabase = "agriinsight"' in harness
    assert 'targetDatabase = "agriinsight_restore_ci"' in harness
    assert '"--publish", "127.0.0.1::5432"' in container_helpers
    assert "farm-operations-fixtures.sql" in harness
    assert "backup-backend-postgres.ps1" in harness
    assert "run-backend-restore-drill.ps1" in harness
    assert '"-MinimumSchemaVersion", "30"' in harness
    assert '"-Mode", "Validate"' in harness
    assert '"-Mode", "Run"' in harness
    assert '"-RestoreDrillScope", "local-or-staging"' in harness
    assert '"-ConfirmRestoreDrill"' in harness
    assert "role_and_rls_gate" in harness
    assert "runtime_tenant_rls_smoke" in harness
    assert "source_sha256" in harness
    assert "restored_flyway_schema_version" in harness
    assert "restored_counts.tenants" in harness
    assert 'docker rm --force $containerId' in container_helpers
    assert "docker logs" not in implementation
    assert "runtimeRootCreatedByThisRun" in harness
    assert "if ($runtimeRootCreatedByThisRun" in harness
    assert '"com.agriinsight.owner"' in container_helpers
    assert 'docker ps --all --no-trunc --filter "id=$containerId"' in container_helpers
    assert "docker system prune" not in implementation
    assert "docker volume prune" not in implementation
    assert "--clean" not in implementation.lower()


def test_restore_drill_validate_mode_accepts_a_checksum_verified_v30_fixture() -> None:
    directory, backup_file = _write_backup_fixture(schema_version=30)
    try:
        result = _run_drill(
            backup_file,
            "-MinimumSchemaVersion",
            "30",
            "-Mode",
            "Validate",
        )
    finally:
        shutil.rmtree(directory)

    assert result.returncode == 0, result.stderr
    assert "RESTORE_DRILL status=PASS mode=Validate scope=local-or-staging" in result.stdout


def test_restore_drill_refuses_an_old_schema_before_any_restore() -> None:
    directory, backup_file = _write_backup_fixture(schema_version=29)
    try:
        result = _run_drill(backup_file, "-MinimumSchemaVersion", "30")
    finally:
        shutil.rmtree(directory)

    assert result.returncode != 0
    assert "Source backup metadata schema is below the requested minimum." in result.stderr


def test_direct_restore_refuses_pre_v30_metadata_before_capacity_or_environment_checks() -> None:
    directory, backup_file = _write_backup_fixture(schema_version=29)
    try:
        result = _run_restore(backup_file)
    finally:
        shutil.rmtree(directory)

    assert result.returncode != 0
    assert "Source backup metadata schema is below the non-lowerable V30 minimum." in result.stderr
    assert "Disk guard is not PASS" not in result.stderr


def test_restore_drill_never_allows_a_weaker_than_v30_minimum() -> None:
    directory, backup_file = _write_backup_fixture(schema_version=30)
    try:
        result = _run_drill(backup_file, "-MinimumSchemaVersion", "29")
    finally:
        shutil.rmtree(directory)

    assert result.returncode != 0
    assert "MinimumSchemaVersion" in result.stderr


def test_restore_drill_refuses_a_tampered_backup_before_any_restore() -> None:
    directory, backup_file = _write_backup_fixture(schema_version=30)
    try:
        backup_file.write_bytes(b"tampered-after-metadata")
        result = _run_drill(backup_file)
    finally:
        shutil.rmtree(directory)

    assert result.returncode != 0
    assert "Backup checksum mismatch; restore drill was not started." in result.stderr


def test_restore_drill_requires_confirmation_before_it_invokes_restore() -> None:
    directory, backup_file = _write_backup_fixture(schema_version=30)
    try:
        result = _run_drill(backup_file, "-Mode", "Run")
    finally:
        shutil.rmtree(directory)

    assert result.returncode != 0
    assert "Run mode requires -ConfirmRestoreDrill." in result.stderr


def test_restore_drill_requires_a_nonproduction_scope_before_it_invokes_restore() -> None:
    directory, backup_file = _write_backup_fixture(schema_version=30)
    try:
        result = _run_drill(
            backup_file,
            "-Mode",
            "Run",
            "-ConfirmRestoreDrill",
        )
    finally:
        shutil.rmtree(directory)

    assert result.returncode != 0
    assert "Run mode requires -RestoreDrillScope local-or-staging." in result.stderr


def test_integrity_helper_locks_sources_and_never_overwrites_a_published_file() -> None:
    directory = Path(
        tempfile.mkdtemp(prefix="restore-integrity-contract-", dir=PROJECT_ROOT)
    )
    destination = directory / "published.dump"
    module = PROJECT_ROOT / "scripts" / "postgres-backup-integrity-helpers.psm1"
    command = f"""
$ErrorActionPreference = 'Stop'
Import-Module -Force '{_powershell_literal(module)}'
$destination = '{_powershell_literal(destination)}'
$first = New-AdjacentTemporaryPath -Destination $destination
[System.IO.File]::WriteAllText($first, 'first')
Publish-NewFile -TemporaryPath $first -Destination $destination
$runningOnWindows = [System.IO.Path]::DirectorySeparatorChar -eq '\'
if ($runningOnWindows) {{
    $sourceLock = Open-ReadLockedFileStream -Path $destination
    try {{
        try {{
            $writer = [System.IO.File]::Open($destination, [System.IO.FileMode]::Open, [System.IO.FileAccess]::Write, [System.IO.FileShare]::ReadWrite)
            $writer.Dispose()
            throw 'source lock allowed a writer'
        }}
        catch [System.IO.IOException] {{
        }}
    }}
    finally {{
        $sourceLock.Dispose()
    }}
}}
$second = New-AdjacentTemporaryPath -Destination $destination
[System.IO.File]::WriteAllText($second, 'second')
$overwriteRejected = $false
try {{
    Publish-NewFile -TemporaryPath $second -Destination $destination
}}
catch {{
    $overwriteRejected = $true
}}
if (-not $overwriteRejected) {{ throw 'published output was overwritten' }}
if ([System.IO.File]::ReadAllText($destination) -cne 'first') {{ throw 'published content changed' }}
"""
    try:
        result = _run_powershell(command)
    finally:
        shutil.rmtree(directory)

    assert result.returncode == 0, result.stderr


def test_restore_target_mutex_refuses_a_concurrent_drill() -> None:
    directory = Path(
        tempfile.mkdtemp(prefix="restore-mutex-contract-", dir=PROJECT_ROOT)
    )
    ready_file = directory / "holder-ready"
    module = PROJECT_ROOT / "scripts" / "postgres-backup-integrity-helpers.psm1"
    holder_command = f"""
$ErrorActionPreference = 'Stop'
Import-Module -Force '{_powershell_literal(module)}'
$mutex = Open-ExclusiveRestoreTargetMutex -EndpointHost '127.0.0.1' -Port '5432' -DatabaseName 'agriinsight_restore_mutex_contract'
try {{
    [System.IO.File]::WriteAllText('{_powershell_literal(ready_file)}', 'ready')
    Start-Sleep -Seconds 10
}}
finally {{
    $mutex.ReleaseMutex()
    $mutex.Dispose()
}}
"""
    holder = _start_powershell(holder_command)
    try:
        deadline = time.monotonic() + 10
        while not ready_file.exists() and time.monotonic() < deadline:
            time.sleep(0.05)
        assert ready_file.exists(), holder.communicate(timeout=5)[1]

        contender = _run_powershell(
            f"""
$ErrorActionPreference = 'Stop'
Import-Module -Force '{_powershell_literal(module)}'
$mutex = Open-ExclusiveRestoreTargetMutex -EndpointHost '127.0.0.1' -Port '5432' -DatabaseName 'agriinsight_restore_mutex_contract'
try {{
    throw 'concurrent restore mutex was unexpectedly acquired'
}}
finally {{
    if ($null -ne $mutex) {{
        $mutex.ReleaseMutex()
        $mutex.Dispose()
    }}
}}
"""
        )
    finally:
        holder.terminate()
        holder.communicate(timeout=10)
        shutil.rmtree(directory)

    assert contender.returncode != 0
    assert "Restore drill is already in progress" in contender.stderr


def test_recovery_docs_keep_a_local_drill_distinct_from_production_rto_proof() -> None:
    documentation = (
        PROJECT_ROOT / "docs" / "backend-deployment.md"
    ).read_text(encoding="utf-8")

    assert "run-backend-restore-drill.ps1" in documentation
    assert "does not\nestablish a production RPO/RTO" in documentation
    assert "MinimumSchemaVersion 30" in documentation
    assert "V30 minimum may only be increased by an operator" in documentation
    assert "atomic no-overwrite move" in documentation
