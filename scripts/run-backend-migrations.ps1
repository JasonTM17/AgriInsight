[CmdletBinding()]
param(
    [switch]$AdoptLegacyOwnership,
    [string]$LegacyOwner
)

Set-StrictMode -Version 2.0
$ErrorActionPreference = "Stop"

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$backendRoot = Join-Path $projectRoot "backend"
$diskGuard = Join-Path $projectRoot "scripts\check-workspace-disk.ps1"
$mavenRunner = Join-Path $projectRoot "scripts\run-backend-tests.ps1"
$roleBootstrap = Join-Path $backendRoot "ops\postgres\bootstrap-roles.sql"
$ownershipAdoption = Join-Path $backendRoot "ops\postgres\adopt-schema-ownership.sql"

function Get-RequiredEnvironmentValue {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Name
    )

    $value = [Environment]::GetEnvironmentVariable($Name)
    if ([string]::IsNullOrWhiteSpace($value)) {
        throw "$Name is required."
    }
    return $value
}

function Invoke-PsqlScript {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Username,
        [Parameter(Mandatory = $true)]
        [string]$Password,
        [Parameter(Mandatory = $true)]
        [string]$ScriptPath,
        [string[]]$Variables = @()
    )

    $env:PGPASSWORD = $Password
    try {
        $arguments = @(
            "--no-password",
            "--no-psqlrc",
            "--set=ON_ERROR_STOP=1",
            "--host=$databaseHost",
            "--port=$databasePort",
            "--dbname=$databaseName",
            "--username=$Username"
        )
        $arguments += $Variables
        $arguments += "--file=$ScriptPath"
        & $psql.Source @arguments
        if ($LASTEXITCODE -ne 0) {
            throw "PostgreSQL script failed: $ScriptPath"
        }
    }
    finally {
        $env:PGPASSWORD = $null
    }
}

$guardOutput = & powershell -ExecutionPolicy Bypass -File $diskGuard 2>&1
$guardExitCode = $LASTEXITCODE
$guardOutput | Write-Output
if ($guardExitCode -ne 0 -or ($guardOutput -join "`n") -notmatch "DISK_GUARD overall=PASS") {
    throw "Disk guard is not PASS; migration was not started."
}

$psql = Get-Command psql -ErrorAction SilentlyContinue
if ($null -eq $psql) {
    throw "psql is required for the PostgreSQL role and ownership gates."
}

$databaseHost = Get-RequiredEnvironmentValue -Name "AGRIINSIGHT_DB_HOST"
$databasePort = Get-RequiredEnvironmentValue -Name "AGRIINSIGHT_DB_PORT"
$databaseName = Get-RequiredEnvironmentValue -Name "AGRIINSIGHT_DB_NAME"
$operatorUsername = Get-RequiredEnvironmentValue -Name "AGRIINSIGHT_DB_OPERATOR_USERNAME"
$operatorPassword = Get-RequiredEnvironmentValue -Name "AGRIINSIGHT_DB_OPERATOR_PASSWORD"
$migrationUrl = Get-RequiredEnvironmentValue -Name "AGRIINSIGHT_FLYWAY_URL"
$migrationUsername = Get-RequiredEnvironmentValue -Name "AGRIINSIGHT_FLYWAY_USERNAME"
$migrationPassword = Get-RequiredEnvironmentValue -Name "AGRIINSIGHT_FLYWAY_PASSWORD"

if ($migrationUsername -ne "agriinsight_migrator") {
    throw "AGRIINSIGHT_FLYWAY_USERNAME must be agriinsight_migrator."
}
if ($operatorUsername -eq $migrationUsername) {
    throw "Operator and migration credentials must use different roles."
}

$expectedTarget = "jdbc:postgresql://${databaseHost}:${databasePort}/${databaseName}"
if (-not $migrationUrl.StartsWith($expectedTarget, [StringComparison]::OrdinalIgnoreCase) -or
        ($migrationUrl.Length -gt $expectedTarget.Length -and
         $migrationUrl[$expectedTarget.Length] -notin @('?', ';'))) {
    throw "AGRIINSIGHT_FLYWAY_URL does not match the guarded PostgreSQL target."
}

if ($AdoptLegacyOwnership -and [string]::IsNullOrWhiteSpace($LegacyOwner)) {
    throw "LegacyOwner is required when AdoptLegacyOwnership is selected."
}
if (-not $AdoptLegacyOwnership -and -not [string]::IsNullOrWhiteSpace($LegacyOwner)) {
    throw "LegacyOwner is valid only with AdoptLegacyOwnership."
}

Invoke-PsqlScript `
    -Username $operatorUsername `
    -Password $operatorPassword `
    -ScriptPath $roleBootstrap

if ($AdoptLegacyOwnership) {
    $adoptionUsername = Get-RequiredEnvironmentValue -Name "AGRIINSIGHT_DB_ADOPTION_USERNAME"
    $adoptionPassword = Get-RequiredEnvironmentValue -Name "AGRIINSIGHT_DB_ADOPTION_PASSWORD"
    Invoke-PsqlScript `
        -Username $adoptionUsername `
        -Password $adoptionPassword `
        -ScriptPath $ownershipAdoption `
        -Variables @(
            "--set=legacy_owner=$LegacyOwner",
            "--set=migration_owner=agriinsight_migrator"
        )
}

$previousFlywayUrl = $env:FLYWAY_URL
$previousFlywayUser = $env:FLYWAY_USER
$previousFlywayPassword = $env:FLYWAY_PASSWORD
try {
    $env:FLYWAY_URL = $migrationUrl
    $env:FLYWAY_USER = $migrationUsername
    $env:FLYWAY_PASSWORD = $migrationPassword

    & powershell -ExecutionPolicy Bypass -File $mavenRunner "flyway:migrate" "flyway:validate"
    if ($LASTEXITCODE -ne 0) {
        throw "Flyway migrate/validate failed."
    }
}
finally {
    $env:FLYWAY_URL = $previousFlywayUrl
    $env:FLYWAY_USER = $previousFlywayUser
    $env:FLYWAY_PASSWORD = $previousFlywayPassword
    $env:PGPASSWORD = $null
}
