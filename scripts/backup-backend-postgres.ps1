[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$BackupFile
)

Set-StrictMode -Version 2.0
$ErrorActionPreference = "Stop"

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$diskGuard = Join-Path $PSScriptRoot "check-workspace-disk.ps1"

function Get-RequiredEnvironmentValue {
    param([Parameter(Mandatory = $true)][string]$Name)

    $value = [Environment]::GetEnvironmentVariable($Name)
    if ([string]::IsNullOrWhiteSpace($value)) {
        throw "$Name is required."
    }
    return $value
}

function Resolve-DDriveFile {
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
    return $resolved
}

function Invoke-ScalarQuery {
    param([Parameter(Mandatory = $true)][string]$Sql)

    $output = & $psql.Source `
        "--no-password" `
        "--no-psqlrc" `
        "--tuples-only" `
        "--no-align" `
        "--host=$databaseHost" `
        "--port=$databasePort" `
        "--dbname=$databaseName" `
        "--username=$operatorUsername" `
        "--command=$Sql"
    if ($LASTEXITCODE -ne 0) {
        throw "PostgreSQL metadata query failed."
    }
    return ($output -join "`n").Trim()
}

$guardOutput = & powershell -ExecutionPolicy Bypass -File $diskGuard 2>&1
$guardExitCode = $LASTEXITCODE
$guardOutput | Write-Output
if ($guardExitCode -ne 0 -or ($guardOutput -join "`n") -notmatch "DISK_GUARD overall=PASS") {
    throw "Disk guard is not PASS; backup was not started."
}

$pgDump = Get-Command pg_dump -ErrorAction SilentlyContinue
$psql = Get-Command psql -ErrorAction SilentlyContinue
if ($null -eq $pgDump -or $null -eq $psql) {
    throw "pg_dump and psql are required."
}

$target = Resolve-DDriveFile -Path $BackupFile
$metadataFile = "$target.metadata.json"
if (Test-Path -LiteralPath $target) {
    throw "Backup file already exists; refusing overwrite: $target"
}
if (Test-Path -LiteralPath $metadataFile) {
    throw "Backup metadata already exists; refusing overwrite: $metadataFile"
}
$parent = Split-Path -Parent $target
if ([string]::IsNullOrWhiteSpace($parent)) {
    throw "BackupFile must include a parent directory."
}
New-Item -ItemType Directory -Force -Path $parent | Out-Null

$databaseHost = Get-RequiredEnvironmentValue -Name "AGRIINSIGHT_DB_HOST"
$databasePort = Get-RequiredEnvironmentValue -Name "AGRIINSIGHT_DB_PORT"
$databaseName = Get-RequiredEnvironmentValue -Name "AGRIINSIGHT_DB_NAME"
$operatorUsername = Get-RequiredEnvironmentValue -Name "AGRIINSIGHT_DB_OPERATOR_USERNAME"
$operatorPassword = Get-RequiredEnvironmentValue -Name "AGRIINSIGHT_DB_OPERATOR_PASSWORD"

$env:PGPASSWORD = $operatorPassword
try {
    $serverVersion = Invoke-ScalarQuery -Sql "SHOW server_version;"
    $schemaVersion = Invoke-ScalarQuery -Sql @"
SELECT COALESCE(max(version), 'missing')
FROM flyway_schema_history
WHERE success = TRUE;
"@

    & $pgDump.Source `
        "--no-password" `
        "--host=$databaseHost" `
        "--port=$databasePort" `
        "--dbname=$databaseName" `
        "--username=$operatorUsername" `
        "--format=custom" `
        "--file=$target"
    if ($LASTEXITCODE -ne 0) {
        throw "pg_dump failed; any partial target is retained and has no valid metadata."
    }

    $hash = Get-FileHash -LiteralPath $target -Algorithm SHA256
    $metadata = [ordered]@{
        format_version = 1
        created_at_utc = [DateTimeOffset]::UtcNow.ToString("O")
        database_name = $databaseName
        postgres_version = $serverVersion
        flyway_schema_version = $schemaVersion
        dump_format = "custom"
        includes_acls = $true
        backup_file = $target
        size_bytes = (Get-Item -LiteralPath $target).Length
        sha256 = $hash.Hash.ToLowerInvariant()
    }
    $metadata | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $metadataFile -Encoding utf8
    Write-Output "BACKUP_BACKEND status=PASS file=$target metadata=$metadataFile sha256=$($metadata.sha256)"
}
finally {
    $env:PGPASSWORD = $null
}
