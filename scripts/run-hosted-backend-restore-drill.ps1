[CmdletBinding()]
param(
    [switch] $HostedCi
)

Set-StrictMode -Version 3.0
$ErrorActionPreference = "Stop"

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
Import-Module (Join-Path $PSScriptRoot "recovery-runtime-helpers.psm1") -Force
Import-Module (Join-Path $PSScriptRoot "hosted-recovery-container-helpers.psm1") -Force

if (-not $HostedCi) {
    throw "The hosted restore harness requires explicit -HostedCi."
}
$runnerTemp = Assert-GitHubHostedRecoveryRunner
Invoke-RecoveryDiskGuard -ProjectRoot $repositoryRoot -HostedCi

if ($null -eq (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw "Docker is required for the hosted restore drill."
}
& docker info --format '{{.ServerVersion}}' | Out-Null
if ($LASTEXITCODE -ne 0) {
    throw "Docker daemon is unavailable for the hosted restore drill."
}

$postgresImage = "postgres:18.0-alpine@sha256:48c8ad3a7284b82be4482a52076d47d879fd6fb084a1cbfccbd551f9331b0e40"
$sourceDatabase = "agriinsight"
$targetDatabase = "agriinsight_restore_ci"
$runId = "$($env:GITHUB_RUN_ID)-$($env:GITHUB_RUN_ATTEMPT)"
if ($runId -cnotmatch '^[1-9][0-9]*-[1-9][0-9]*$') {
    throw "GitHub run identity is invalid for the hosted restore drill."
}

$runtimeRoot = Join-Path $runnerTemp "agriinsight-restore-drill-$runId"
$safeEvidenceRoot = Join-Path $repositoryRoot "artifacts/recovery-evidence/$runId"
$clientDirectory = Join-Path $runtimeRoot "postgres-clients"
$sourceEnvironmentFile = Join-Path $runtimeRoot "source.env"
$targetEnvironmentFile = Join-Path $runtimeRoot "target.env"
$backupFile = Join-Path $runtimeRoot "current.dump"
$operatorPassword = [Guid]::NewGuid().ToString("N")
$migrationPassword = [Guid]::NewGuid().ToString("N")
$runtimePassword = [Guid]::NewGuid().ToString("N")
$sourceName = "agriinsight-recovery-source-$runId"
$targetName = "agriinsight-recovery-target-$runId"
$containerIds = [System.Collections.Generic.List[string]]::new()
$previousPath = $env:PATH
$previousRecoveryRoot = $env:AGRIINSIGHT_RECOVERY_ALLOWED_ROOT
$runError = $null
$cleanupError = $null
$runtimeRootCreatedByThisRun = $false

function Invoke-Psql {
    param(
        [Parameter(Mandatory = $true)] [string] $Port,
        [Parameter(Mandatory = $true)] [string] $Database,
        [Parameter(Mandatory = $true)] [string] $Username,
        [Parameter(Mandatory = $true)] [string] $Password,
        [string] $Script,
        [string] $StandardInput
    )

    $arguments = @(
        "--no-password", "--no-psqlrc", "--set=ON_ERROR_STOP=1",
        "--host=127.0.0.1", "--port=$Port", "--dbname=$Database", "--username=$Username"
    )
    if (-not [string]::IsNullOrWhiteSpace($Script)) {
        $arguments += "--file=$Script"
    }
    $env:PGPASSWORD = $Password
    try {
        if (-not [string]::IsNullOrWhiteSpace($StandardInput)) {
            $StandardInput | & psql @arguments | Out-Null
        }
        else {
            & psql @arguments | Out-Null
        }
        if ($LASTEXITCODE -ne 0) {
            throw "Hosted recovery PostgreSQL command failed."
        }
    }
    finally {
        $env:PGPASSWORD = $null
    }
}

function Set-HostedRolePasswords {
    param([string] $Port, [string] $Database)

    $sql = @"
ALTER ROLE agriinsight_migrator PASSWORD '$migrationPassword';
ALTER ROLE agriinsight_runtime PASSWORD '$runtimePassword';
"@
    Invoke-Psql -Port $Port -Database $Database -Username "postgres" -Password $operatorPassword -StandardInput $sql
}

function Invoke-GuardedPowerShell {
    param([string] $Script, [string[]] $Arguments, [string] $ExpectedOutput)

    $powerShellCommand = Get-RecoveryPowerShellCommand
    $output = @(& $powerShellCommand -NoProfile -ExecutionPolicy Bypass -File $Script @Arguments)
    $exitCode = $LASTEXITCODE
    $output | Write-Output
    if ($exitCode -ne 0 -or ($output -join "`n") -notmatch $ExpectedOutput) {
        throw "Hosted restore drill command did not produce its required PASS evidence."
    }
}

function Set-SourceEnvironment {
    param([string] $Port)

    $env:AGRIINSIGHT_DB_HOST = "127.0.0.1"
    $env:AGRIINSIGHT_DB_PORT = $Port
    $env:AGRIINSIGHT_DB_NAME = $sourceDatabase
    $env:AGRIINSIGHT_DB_OPERATOR_USERNAME = "postgres"
    $env:AGRIINSIGHT_DB_OPERATOR_PASSWORD = $operatorPassword
}

function Set-TargetEnvironment {
    param([string] $Port)

    $env:AGRIINSIGHT_RESTORE_DRILL_HOST = "127.0.0.1"
    $env:AGRIINSIGHT_RESTORE_DRILL_PORT = $Port
    $env:AGRIINSIGHT_RESTORE_DRILL_ALLOWED_HOSTS = "127.0.0.1"
    $env:AGRIINSIGHT_RESTORE_DRILL_TARGET_DATABASE = $targetDatabase
    $env:AGRIINSIGHT_DB_OPERATOR_USERNAME = "postgres"
    $env:AGRIINSIGHT_DB_OPERATOR_PASSWORD = $operatorPassword
    $env:AGRIINSIGHT_FLYWAY_USERNAME = "agriinsight_migrator"
    $env:AGRIINSIGHT_FLYWAY_PASSWORD = $migrationPassword
    $env:AGRIINSIGHT_DB_RUNTIME_USERNAME = "agriinsight_runtime"
    $env:AGRIINSIGHT_DB_RUNTIME_PASSWORD = $runtimePassword
}

try {
    if (Test-Path -LiteralPath $runtimeRoot) {
        throw "Hosted restore runtime already exists; refusing to overwrite it."
    }
    if (Test-Path -LiteralPath $safeEvidenceRoot) {
        throw "Hosted restore evidence directory already exists; refusing to overwrite it."
    }
    New-Item -ItemType Directory -Path $runtimeRoot | Out-Null
    $runtimeRootCreatedByThisRun = $true
    New-Item -ItemType Directory -Path $safeEvidenceRoot | Out-Null
    @("POSTGRES_DB=$sourceDatabase", "POSTGRES_USER=postgres", "POSTGRES_PASSWORD=$operatorPassword") |
        Set-Content -LiteralPath $sourceEnvironmentFile -Encoding utf8NoBOM
    @("POSTGRES_DB=$targetDatabase", "POSTGRES_USER=postgres", "POSTGRES_PASSWORD=$operatorPassword") |
        Set-Content -LiteralPath $targetEnvironmentFile -Encoding utf8NoBOM
    & chmod 600 $sourceEnvironmentFile $targetEnvironmentFile
    if ($LASTEXITCODE -ne 0) { throw "Could not protect hosted recovery environment files." }

    Write-PostgresClientShims -Directory $clientDirectory -Image $postgresImage -RecoveryRoot $runtimeRoot -RepositoryRoot $repositoryRoot
    $env:PATH = "$clientDirectory$([System.IO.Path]::PathSeparator)$previousPath"
    $env:AGRIINSIGHT_RECOVERY_ALLOWED_ROOT = $runtimeRoot

    $sourceId = Start-OwnedPostgresContainer -Name $sourceName -Database $sourceDatabase -EnvironmentFile $sourceEnvironmentFile -Image $postgresImage -RunId $runId
    $containerIds.Add($sourceId)
    $targetId = Start-OwnedPostgresContainer -Name $targetName -Database $targetDatabase -EnvironmentFile $targetEnvironmentFile -Image $postgresImage -RunId $runId
    $containerIds.Add($targetId)
    Wait-OwnedPostgresContainer -ContainerId $sourceId
    Wait-OwnedPostgresContainer -ContainerId $targetId
    $sourcePort = Get-OwnedPostgresPort -ContainerId $sourceId
    $targetPort = Get-OwnedPostgresPort -ContainerId $targetId

    $roleBootstrap = Join-Path $repositoryRoot "backend/ops/postgres/bootstrap-roles.sql"
    foreach ($databaseTarget in @(
        [pscustomobject]@{ Port = $sourcePort; Database = $sourceDatabase },
        [pscustomobject]@{ Port = $targetPort; Database = $targetDatabase }
    )) {
        Invoke-Psql -Port $databaseTarget.Port -Database $databaseTarget.Database -Username "postgres" -Password $operatorPassword -Script $roleBootstrap
        Set-HostedRolePasswords -Port $databaseTarget.Port -Database $databaseTarget.Database
    }

    $env:FLYWAY_URL = "jdbc:postgresql://127.0.0.1:$sourcePort/$sourceDatabase"
    $env:FLYWAY_USER = "agriinsight_migrator"
    $env:FLYWAY_PASSWORD = $migrationPassword
    Invoke-GuardedPowerShell -Script (Join-Path $PSScriptRoot "run-backend-tests.ps1") -Arguments @("-HostedCi", "flyway:migrate", "flyway:validate") -ExpectedOutput "BUILD SUCCESS"

    $fixture = Join-Path $repositoryRoot "backend/src/test/resources/sql/farm-operations-fixtures.sql"
    Invoke-Psql -Port $sourcePort -Database $sourceDatabase -Username "postgres" -Password $operatorPassword -Script $fixture

    Set-SourceEnvironment -Port $sourcePort
    Invoke-GuardedPowerShell -Script (Join-Path $PSScriptRoot "backup-backend-postgres.ps1") -Arguments @("-BackupFile", $backupFile, "-HostedCi") -ExpectedOutput "BACKUP_BACKEND status=PASS"

    $drillScript = Join-Path $PSScriptRoot "run-backend-restore-drill.ps1"
    Invoke-GuardedPowerShell -Script $drillScript -Arguments @("-BackupFile", $backupFile, "-MinimumSchemaVersion", "30", "-Mode", "Validate", "-HostedCi") -ExpectedOutput "RESTORE_DRILL status=PASS mode=Validate"
    Set-TargetEnvironment -Port $targetPort
    Invoke-GuardedPowerShell -Script $drillScript -Arguments @("-BackupFile", $backupFile, "-MinimumSchemaVersion", "30", "-Mode", "Run", "-RestoreDrillScope", "local-or-staging", "-ConfirmRestoreDrill", "-HostedCi") -ExpectedOutput "RESTORE_DRILL status=PASS mode=Run"

    $metadataFile = "$backupFile.metadata.json"
    $reportFile = @(Get-ChildItem -LiteralPath $runtimeRoot -Filter "current.dump.restore-*.json" -File)
    if ($reportFile.Count -ne 1) { throw "Hosted restore drill did not create exactly one report." }
    $metadata = Get-Content -LiteralPath $metadataFile -Raw | ConvertFrom-Json -ErrorAction Stop
    $report = Get-Content -LiteralPath $reportFile[0].FullName -Raw | ConvertFrom-Json -ErrorAction Stop
    if ($metadata.sha256 -cne $report.source_sha256 -or
            [int]$metadata.flyway_schema_version -lt 30 -or
            [int]$report.restored_flyway_schema_version -lt 30 -or
            $report.role_and_rls_gate -cne "PASS" -or
            $report.runtime_tenant_rls_smoke -cne "PASS" -or
            [int64]$report.restored_counts.tenants -ne 2) {
        throw "Hosted restore evidence does not satisfy checksum, V30, role, RLS, or tenant-count gates."
    }

    Copy-Item -LiteralPath $metadataFile -Destination $safeEvidenceRoot
    Copy-Item -LiteralPath $reportFile[0].FullName -Destination $safeEvidenceRoot
    [ordered]@{
        format_version = 1
        status = "PASS"
        scope = "hosted-local-or-staging"
        source_sha256 = $report.source_sha256
        source_flyway_schema_version = [int]$metadata.flyway_schema_version
        restored_flyway_schema_version = [int]$report.restored_flyway_schema_version
        restored_tenants = [int64]$report.restored_counts.tenants
        role_and_rls_gate = $report.role_and_rls_gate
        runtime_tenant_rls_smoke = $report.runtime_tenant_rls_smoke
        elapsed_seconds = [double]$report.elapsed_seconds
    } | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $safeEvidenceRoot "summary.json") -Encoding utf8
    Write-Output "HOSTED_RESTORE_DRILL=PASS schema_version=$($report.restored_flyway_schema_version) tenants=$($report.restored_counts.tenants) evidence=$safeEvidenceRoot"
}
catch {
    $runError = $_
}
finally {
    if ($containerIds.Count -gt 0) {
        try {
            Remove-OwnedRecoveryContainers -ContainerIds @($containerIds) -RunId $runId
        }
        catch {
            $cleanupError = $_
        }
    }
    $env:PATH = $previousPath
    $env:AGRIINSIGHT_RECOVERY_ALLOWED_ROOT = $previousRecoveryRoot
    if ($runtimeRootCreatedByThisRun -and (Test-Path -LiteralPath $runtimeRoot)) {
        try {
            Remove-Item -LiteralPath $runtimeRoot -Recurse -Force
        }
        catch {
            if ($null -eq $cleanupError) {
                $cleanupError = $_
            }
            else {
                $cleanupError = [System.Exception]::new("$($cleanupError.Exception.Message) Runtime cleanup also failed: $($_.Exception.Message)")
            }
        }
    }
}

if ($null -ne $runError) {
    if ($null -ne $cleanupError) {
        throw "$($runError.Exception.Message) Cleanup also failed: $($cleanupError.Exception.Message)"
    }
    throw $runError
}
if ($null -ne $cleanupError) { throw $cleanupError }
