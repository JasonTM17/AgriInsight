[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$BackupFile
)

Set-StrictMode -Version 2.0
$ErrorActionPreference = "Stop"

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$diskGuard = Join-Path $PSScriptRoot "check-workspace-disk.ps1"
$backendRunner = Join-Path $PSScriptRoot "run-backend-tests.ps1"
$roleBootstrap = Join-Path $projectRoot "backend\ops\postgres\bootstrap-roles.sql"

function Get-RequiredEnvironmentValue {
    param([Parameter(Mandatory = $true)][string]$Name)

    $value = [Environment]::GetEnvironmentVariable($Name)
    if ([string]::IsNullOrWhiteSpace($value)) {
        throw "$Name is required."
    }
    return $value
}

function Resolve-ExistingDDriveFile {
    param([Parameter(Mandatory = $true)][string]$Path)

    $candidate = if ([System.IO.Path]::IsPathRooted($Path)) {
        $Path
    } else {
        Join-Path $projectRoot $Path
    }
    $resolved = [System.IO.Path]::GetFullPath($candidate)
    if ([System.IO.Path]::GetPathRoot($resolved) -ne "D:\") {
        throw "BackupFile must resolve to the D drive; received $resolved."
    }
    if (-not (Test-Path -LiteralPath $resolved -PathType Leaf)) {
        throw "Backup file does not exist: $resolved"
    }
    return $resolved
}

function Invoke-Psql {
    param(
        [Parameter(Mandatory = $true)][string]$Username,
        [Parameter(Mandatory = $true)][string]$Password,
        [string]$Sql,
        [string]$Script
    )

    $env:PGPASSWORD = $Password
    try {
        $arguments = @(
            "--no-password", "--no-psqlrc", "--set=ON_ERROR_STOP=1",
            "--host=$databaseHost", "--port=$databasePort",
            "--dbname=$databaseName", "--username=$Username"
        )
        if (-not [string]::IsNullOrWhiteSpace($Sql)) {
            $arguments += @("--tuples-only", "--no-align", "--command=$Sql")
        } elseif (-not [string]::IsNullOrWhiteSpace($Script)) {
            $arguments += "--file=$Script"
        } else {
            throw "Invoke-Psql requires Sql or Script."
        }
        $output = & $psql.Source @arguments
        if ($LASTEXITCODE -ne 0) {
            throw "PostgreSQL validation command failed."
        }
        return ($output -join "`n").Trim()
    }
    finally {
        $env:PGPASSWORD = $null
    }
}

$guardOutput = & powershell -ExecutionPolicy Bypass -File $diskGuard 2>&1
$guardExitCode = $LASTEXITCODE
$guardOutput | Write-Output
if ($guardExitCode -ne 0 -or ($guardOutput -join "`n") -notmatch "DISK_GUARD overall=PASS") {
    throw "Disk guard is not PASS; restore was not started."
}

$psql = Get-Command psql -ErrorAction SilentlyContinue
$pgRestore = Get-Command pg_restore -ErrorAction SilentlyContinue
if ($null -eq $psql -or $null -eq $pgRestore) {
    throw "psql and pg_restore are required."
}

$source = Resolve-ExistingDDriveFile -Path $BackupFile
$metadataFile = "$source.metadata.json"
if (-not (Test-Path -LiteralPath $metadataFile -PathType Leaf)) {
    throw "Backup metadata is required: $metadataFile"
}
$metadata = Get-Content -LiteralPath $metadataFile -Raw | ConvertFrom-Json
$actualHash = (Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash.ToLowerInvariant()
if ($actualHash -ne $metadata.sha256) {
    throw "Backup checksum mismatch; restore was not started."
}

$databaseHost = Get-RequiredEnvironmentValue -Name "AGRIINSIGHT_DB_HOST"
$databasePort = Get-RequiredEnvironmentValue -Name "AGRIINSIGHT_DB_PORT"
$databaseName = Get-RequiredEnvironmentValue -Name "AGRIINSIGHT_DB_NAME"
$operatorUsername = Get-RequiredEnvironmentValue -Name "AGRIINSIGHT_DB_OPERATOR_USERNAME"
$operatorPassword = Get-RequiredEnvironmentValue -Name "AGRIINSIGHT_DB_OPERATOR_PASSWORD"
$migrationUsername = Get-RequiredEnvironmentValue -Name "AGRIINSIGHT_FLYWAY_USERNAME"
$migrationPassword = Get-RequiredEnvironmentValue -Name "AGRIINSIGHT_FLYWAY_PASSWORD"
$runtimeUsername = Get-RequiredEnvironmentValue -Name "AGRIINSIGHT_DB_RUNTIME_USERNAME"
$runtimePassword = Get-RequiredEnvironmentValue -Name "AGRIINSIGHT_DB_RUNTIME_PASSWORD"

if ($migrationUsername -ne "agriinsight_migrator" -or $runtimeUsername -ne "agriinsight_runtime") {
    throw "Restore requires the expected migration and runtime roles."
}
$tableCount = Invoke-Psql -Username $operatorUsername -Password $operatorPassword -Sql @"
SELECT count(*)
FROM pg_catalog.pg_class AS relation
JOIN pg_catalog.pg_namespace AS namespace ON namespace.oid = relation.relnamespace
WHERE relation.relkind IN ('r', 'p')
  AND namespace.nspname NOT IN ('pg_catalog', 'information_schema');
"@
if ([int64]$tableCount -ne 0) {
    throw "Target database must be pre-created and empty; found $tableCount user tables."
}

$watch = [System.Diagnostics.Stopwatch]::StartNew()
Invoke-Psql -Username $operatorUsername -Password $operatorPassword -Script $roleBootstrap | Out-Null

$env:PGPASSWORD = $migrationPassword
try {
    & $pgRestore.Source `
        "--no-password" `
        "--host=$databaseHost" `
        "--port=$databasePort" `
        "--dbname=$databaseName" `
        "--username=$migrationUsername" `
        "--no-owner" `
        "--single-transaction" `
        "--exit-on-error" `
        $source
    if ($LASTEXITCODE -ne 0) {
        throw "pg_restore failed; the target is retained for diagnosis."
    }
}
finally {
    $env:PGPASSWORD = $null
}

$previousFlywayUrl = $env:FLYWAY_URL
$previousFlywayUser = $env:FLYWAY_USER
$previousFlywayPassword = $env:FLYWAY_PASSWORD
try {
    $env:FLYWAY_URL = "jdbc:postgresql://${databaseHost}:${databasePort}/${databaseName}"
    $env:FLYWAY_USER = $migrationUsername
    $env:FLYWAY_PASSWORD = $migrationPassword
    & powershell -ExecutionPolicy Bypass -File $backendRunner "flyway:validate"
    if ($LASTEXITCODE -ne 0) {
        throw "Flyway validation failed after restore."
    }
}
finally {
    $env:FLYWAY_URL = $previousFlywayUrl
    $env:FLYWAY_USER = $previousFlywayUser
    $env:FLYWAY_PASSWORD = $previousFlywayPassword
}

$roleAndRlsGate = Invoke-Psql -Username $operatorUsername -Password $operatorPassword -Sql @"
SELECT EXISTS (
    SELECT 1 FROM pg_catalog.pg_roles
    WHERE rolname = 'agriinsight_integration' AND NOT rolcanlogin AND NOT rolbypassrls)
AND EXISTS (
    SELECT 1 FROM pg_catalog.pg_class
    WHERE relname = 'outbox_events' AND relrowsecurity AND relforcerowsecurity);
"@
if ($roleAndRlsGate -ne "t") {
    throw "Integration-role or outbox RLS restore gate failed."
}
$runtimeSchemaRows = Invoke-Psql `
    -Username $runtimeUsername `
    -Password $runtimePassword `
    -Sql "SELECT count(*) FROM flyway_schema_history WHERE success = TRUE;"
$restoredCounts = Invoke-Psql -Username $operatorUsername -Password $operatorPassword -Sql @"
SELECT json_build_object(
    'tenants', (SELECT count(*) FROM tenants),
    'farms', (SELECT count(*) FROM farms),
    'outbox_events', (SELECT count(*) FROM outbox_events))::text;
"@
$watch.Stop()

$reportFile = "$source.restore-$([DateTimeOffset]::UtcNow.ToString('yyyyMMddTHHmmssZ')).json"
if (Test-Path -LiteralPath $reportFile) {
    throw "Restore report already exists; refusing overwrite: $reportFile"
}
[ordered]@{
    format_version = 1
    restored_at_utc = [DateTimeOffset]::UtcNow.ToString("O")
    source_backup = $source
    source_sha256 = $actualHash
    target_database = $databaseName
    elapsed_seconds = [Math]::Round($watch.Elapsed.TotalSeconds, 3)
    role_and_rls_gate = "PASS"
    runtime_schema_history_rows = [int64]$runtimeSchemaRows
    restored_counts = ($restoredCounts | ConvertFrom-Json)
} | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $reportFile -Encoding utf8

Write-Output "RESTORE_BACKEND status=PASS database=$databaseName report=$reportFile elapsed_seconds=$([Math]::Round($watch.Elapsed.TotalSeconds, 3))"
