[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$BackupFile,
    [Parameter(Mandatory = $true)]
    [ValidateSet("local-or-staging")]
    [string]$RestoreDrillScope,
    [ValidateRange(30, 10000)]
    [int]$MinimumSchemaVersion = 30,
    [switch]$HostedCi
)

Set-StrictMode -Version 2.0
$ErrorActionPreference = "Stop"

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$backendRunner = Join-Path $PSScriptRoot "run-backend-tests.ps1"
$roleBootstrap = Join-Path $projectRoot "backend\ops\postgres\bootstrap-roles.sql"
Import-Module (Join-Path $PSScriptRoot "postgres-backup-integrity-helpers.psm1") -Force
Import-Module (Join-Path $PSScriptRoot "recovery-runtime-helpers.psm1") -Force

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
    $resolved = Assert-DDrivePathWithoutReparsePoints -Path $candidate
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
            "--no-password", "--no-psqlrc", "--quiet", "--set=ON_ERROR_STOP=1",
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

$source = Resolve-ExistingDDriveFile -Path $BackupFile
$metadataFile = "$source.metadata.json"
if (-not (Test-Path -LiteralPath $metadataFile -PathType Leaf)) {
    throw "Backup metadata is required: $metadataFile"
}
$metadata = Get-Content -LiteralPath $metadataFile -Raw | ConvertFrom-Json -ErrorAction Stop
$sourceSchemaVersion = [string]$metadata.flyway_schema_version
[int]$parsedSourceSchemaVersion = 0
if ($sourceSchemaVersion -notmatch '^[1-9][0-9]*$' -or
    -not [int]::TryParse($sourceSchemaVersion, [ref]$parsedSourceSchemaVersion) -or
    $parsedSourceSchemaVersion -lt $MinimumSchemaVersion) {
    throw "Source backup metadata schema is below the non-lowerable V30 minimum."
}
$sourceDatabaseName = [string]$metadata.database_name
if ([string]::IsNullOrWhiteSpace($sourceDatabaseName)) {
    throw "Backup metadata database_name is required."
}

Invoke-RecoveryDiskGuard -ProjectRoot $projectRoot -HostedCi:$HostedCi

$psql = Get-Command psql -ErrorAction SilentlyContinue
$pgRestore = Get-Command pg_restore -ErrorAction SilentlyContinue
if ($null -eq $psql -or $null -eq $pgRestore) {
    throw "psql and pg_restore are required."
}
$psqlVersion = ((& $psql.Source --version) -join " ").Trim()
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($psqlVersion)) {
    throw "Could not determine psql version."
}
$pgRestoreVersion = ((& $pgRestore.Source --version) -join " ").Trim()
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($pgRestoreVersion)) {
    throw "Could not determine pg_restore version."
}

$sourceReadLock = Open-ReadLockedFileStream -Path $source
try {
$actualHash = Get-Sha256Hex -Path $source
if ($actualHash -ne $metadata.sha256) {
    throw "Backup checksum mismatch; restore was not started."
}

$databaseHost = Get-RequiredEnvironmentValue -Name "AGRIINSIGHT_RESTORE_DRILL_HOST"
$normalizedDatabaseHost = $databaseHost.Trim().TrimEnd('.').ToLowerInvariant()
if ($normalizedDatabaseHost -cne '127.0.0.1') {
    throw "AGRIINSIGHT_RESTORE_DRILL_HOST must be the literal IPv4 loopback address 127.0.0.1 until a remote TLS provider contract is approved."
}
$allowedHosts = @(
    (Get-RequiredEnvironmentValue -Name "AGRIINSIGHT_RESTORE_DRILL_ALLOWED_HOSTS").Split(',') |
        ForEach-Object { $_.Trim().TrimEnd('.').ToLowerInvariant() } |
        Where-Object { -not [string]::IsNullOrWhiteSpace($_) }
)
if ($allowedHosts.Count -eq 0 -or $allowedHosts -cnotcontains $normalizedDatabaseHost) {
    throw "AGRIINSIGHT_RESTORE_DRILL_HOST is not in AGRIINSIGHT_RESTORE_DRILL_ALLOWED_HOSTS."
}
$databaseHost = $normalizedDatabaseHost
$databasePort = Get-RequiredEnvironmentValue -Name "AGRIINSIGHT_RESTORE_DRILL_PORT"
[int]$parsedDatabasePort = 0
if (-not [int]::TryParse($databasePort, [ref]$parsedDatabasePort) -or
    $parsedDatabasePort -lt 1 -or $parsedDatabasePort -gt 65535) {
    throw "AGRIINSIGHT_RESTORE_DRILL_PORT must be an integer from 1 through 65535."
}
$databasePort = $parsedDatabasePort.ToString([System.Globalization.CultureInfo]::InvariantCulture)
$databaseName = Get-RequiredEnvironmentValue -Name "AGRIINSIGHT_RESTORE_DRILL_TARGET_DATABASE"
if ($databaseName -cnotmatch '^agriinsight_restore_[a-z0-9_]{1,48}$') {
    throw "AGRIINSIGHT_RESTORE_DRILL_TARGET_DATABASE must name a dedicated lowercase agriinsight_restore_* database."
}
if ($databaseName -ceq $sourceDatabaseName) {
    throw "Restore target must differ from the backup source database."
}
$operatorUsername = Get-RequiredEnvironmentValue -Name "AGRIINSIGHT_DB_OPERATOR_USERNAME"
$operatorPassword = Get-RequiredEnvironmentValue -Name "AGRIINSIGHT_DB_OPERATOR_PASSWORD"
$migrationUsername = Get-RequiredEnvironmentValue -Name "AGRIINSIGHT_FLYWAY_USERNAME"
$migrationPassword = Get-RequiredEnvironmentValue -Name "AGRIINSIGHT_FLYWAY_PASSWORD"
$runtimeUsername = Get-RequiredEnvironmentValue -Name "AGRIINSIGHT_DB_RUNTIME_USERNAME"
$runtimePassword = Get-RequiredEnvironmentValue -Name "AGRIINSIGHT_DB_RUNTIME_PASSWORD"

if ($migrationUsername -ne "agriinsight_migrator" -or $runtimeUsername -ne "agriinsight_runtime") {
    throw "Restore requires the expected migration and runtime roles."
}
$targetRestoreMutex = Open-ExclusiveRestoreTargetMutex -EndpointHost $databaseHost -Port $databasePort -DatabaseName $databaseName
try {
$currentDatabaseName = Invoke-Psql -Username $operatorUsername -Password $operatorPassword -Sql "SELECT current_database();"
if ($currentDatabaseName -cne $databaseName) {
    throw "Connected database does not match AGRIINSIGHT_RESTORE_DRILL_TARGET_DATABASE."
}
$additionalDatabaseCount = Invoke-Psql -Username $operatorUsername -Password $operatorPassword -Sql @"
SELECT count(*)
FROM pg_catalog.pg_database
WHERE datallowconn
  AND NOT datistemplate
  AND datname NOT IN ('$databaseName', 'postgres');
"@
if ([int64]$additionalDatabaseCount -ne 0) {
    throw "Dedicated restore-drill cluster contains another non-system database."
}
$foreignActivityCount = Invoke-Psql -Username $operatorUsername -Password $operatorPassword -Sql @"
SELECT count(*)
FROM pg_catalog.pg_stat_activity
WHERE pid <> pg_backend_pid()
  AND datname IS NOT NULL
  AND datname <> '$databaseName';
"@
if ([int64]$foreignActivityCount -ne 0) {
    throw "Dedicated restore-drill cluster has active connections outside the target database."
}
$unsafeObjectCount = Invoke-Psql -Username $operatorUsername -Password $operatorPassword -Sql @"
SELECT count(*)
FROM (
    SELECT relation.oid
    FROM pg_catalog.pg_class AS relation
    JOIN pg_catalog.pg_namespace AS namespace ON namespace.oid = relation.relnamespace
    WHERE namespace.nspname NOT IN ('pg_catalog', 'information_schema')
      AND namespace.nspname NOT LIKE 'pg_toast%'
    UNION ALL
    SELECT routine.oid
    FROM pg_catalog.pg_proc AS routine
    JOIN pg_catalog.pg_namespace AS namespace ON namespace.oid = routine.pronamespace
    WHERE namespace.nspname NOT IN ('pg_catalog', 'information_schema')
      AND namespace.nspname NOT LIKE 'pg_toast%'
    UNION ALL
    SELECT type.oid
    FROM pg_catalog.pg_type AS type
    JOIN pg_catalog.pg_namespace AS namespace ON namespace.oid = type.typnamespace
    WHERE namespace.nspname NOT IN ('pg_catalog', 'information_schema')
      AND namespace.nspname NOT LIKE 'pg_toast%'
    UNION ALL
    SELECT extension.oid
    FROM pg_catalog.pg_extension AS extension
    WHERE extension.extname <> 'plpgsql'
    UNION ALL
    SELECT namespace.oid
    FROM pg_catalog.pg_namespace AS namespace
    WHERE namespace.nspname NOT IN ('pg_catalog', 'information_schema', 'public')
      AND namespace.nspname NOT LIKE 'pg_toast%'
) AS unsafe_object;
"@
if ([int64]$unsafeObjectCount -ne 0) {
    throw "Target database must be pre-created and empty; found $unsafeObjectCount user objects."
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
    $powerShellCommand = Get-RecoveryPowerShellCommand
    $backendArguments = @("-NoProfile", "-ExecutionPolicy", "Bypass", "-File", $backendRunner)
    if ($HostedCi) { $backendArguments += "-HostedCi" }
    $backendArguments += "flyway:validate"
    & $powerShellCommand @backendArguments
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
$noContextTenantResult = Invoke-Psql `
    -Username $runtimeUsername `
    -Password $runtimePassword `
    -Sql "SELECT count(*) = 0 FROM tenants;"
if ($noContextTenantResult -cne "t") {
    throw "Runtime tenant RLS smoke failed after restore."
}
$tenantIds = @(
    (Invoke-Psql -Username $operatorUsername -Password $operatorPassword -Sql "SELECT id FROM tenants ORDER BY id LIMIT 2;") -split "`r?`n" |
        Where-Object { -not [string]::IsNullOrWhiteSpace($_) }
)
if ($tenantIds.Count -ne 2 -or $tenantIds[0] -cnotmatch '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$' -or
    $tenantIds[1] -cnotmatch '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$') {
    throw "Restore RLS smoke requires two canonical tenant identifiers."
}
$tenantBoundaryResult = Invoke-Psql -Username $runtimeUsername -Password $runtimePassword -Sql @"
BEGIN;
SET LOCAL app.tenant_id = '$($tenantIds[0])';
SELECT count(*) = 1
    AND NOT EXISTS (SELECT 1 FROM tenants WHERE id = '$($tenantIds[1])')
FROM tenants;
COMMIT;
"@
if ($tenantBoundaryResult -cne "t") {
    throw "Runtime tenant RLS smoke failed after restore."
}
$runtimeSchemaRows = Invoke-Psql `
    -Username $runtimeUsername `
    -Password $runtimePassword `
    -Sql "SELECT count(*) FROM flyway_schema_history WHERE success = TRUE;"
$restoredFlywaySchemaVersion = Invoke-Psql -Username $operatorUsername -Password $operatorPassword -Sql @"
SELECT COALESCE(
    (
        SELECT version
        FROM flyway_schema_history
        WHERE success = TRUE
          AND version IS NOT NULL
          AND version <> ''
        ORDER BY installed_rank DESC
        LIMIT 1
    ),
    'missing'
);
"@
$restoredCounts = Invoke-Psql -Username $operatorUsername -Password $operatorPassword -Sql @"
SELECT json_build_object(
    'tenants', (SELECT count(*) FROM tenants),
    'farms', (SELECT count(*) FROM farms),
    'outbox_events', (SELECT count(*) FROM outbox_events))::text;
"@
$watch.Stop()

$reportFile = "$source.restore-$([DateTimeOffset]::UtcNow.ToString('yyyyMMddTHHmmssfffZ'))-$([Guid]::NewGuid().ToString('N')).json"
$temporaryReportFile = New-AdjacentTemporaryPath -Destination $reportFile
[ordered]@{
    format_version = 1
    restored_at_utc = [DateTimeOffset]::UtcNow.ToString("O")
    source_backup = $source
    source_sha256 = $actualHash
    source_backup_metadata = [ordered]@{
        format_version = $metadata.format_version
        flyway_schema_version = $sourceSchemaVersion
        postgres_version = $metadata.postgres_version
        created_at_utc = $metadata.created_at_utc
    }
    target_database = $databaseName
    restore_drill_scope = $RestoreDrillScope
    elapsed_seconds = [Math]::Round($watch.Elapsed.TotalSeconds, 3)
    role_and_rls_gate = "PASS"
    runtime_tenant_rls_smoke = "PASS"
    psql_version = $psqlVersion
    pg_restore_version = $pgRestoreVersion
    runtime_schema_history_rows = [int64]$runtimeSchemaRows
    restored_flyway_schema_version = $restoredFlywaySchemaVersion
    restored_counts = ($restoredCounts | ConvertFrom-Json)
} | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $temporaryReportFile -Encoding utf8
Publish-NewFile -TemporaryPath $temporaryReportFile -Destination $reportFile

Write-Output "RESTORE_BACKEND status=PASS database=$databaseName report=$reportFile elapsed_seconds=$([Math]::Round($watch.Elapsed.TotalSeconds, 3))"
}
finally {
    $targetRestoreMutex.ReleaseMutex()
    $targetRestoreMutex.Dispose()
}
}
finally {
    $sourceReadLock.Dispose()
}
