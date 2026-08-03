[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string] $BackupFile,

    [ValidateSet("Validate", "Run")]
    [string] $Mode = "Validate",

    [ValidateRange(30, 10000)]
    [int] $MinimumSchemaVersion = 30,

    [ValidateSet("local-or-staging")]
    [string] $RestoreDrillScope,

    [switch] $ConfirmRestoreDrill,

    [switch] $HostedCi
)

Set-StrictMode -Version 3.0
$ErrorActionPreference = "Stop"

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$restoreScript = Join-Path $PSScriptRoot "restore-backend-postgres.ps1"
Import-Module (Join-Path $PSScriptRoot "postgres-backup-integrity-helpers.psm1") -Force
Import-Module (Join-Path $PSScriptRoot "recovery-runtime-helpers.psm1") -Force

function Resolve-ExistingDDriveFile {
    param([string] $Path)

    $candidate = if ([System.IO.Path]::IsPathRooted($Path)) {
        $Path
    }
    else {
        Join-Path $projectRoot $Path
    }
    $resolved = Assert-DDrivePathWithoutReparsePoints -Path $candidate
    if (-not (Test-Path -LiteralPath $resolved -PathType Leaf)) {
        throw "Backup file does not exist: $resolved"
    }
    return $resolved
}

function Get-RequiredPropertyValue {
    param([object] $Object, [string] $Name, [string] $Purpose)

    $property = $Object.PSObject.Properties[$Name]
    if ($null -eq $property -or $null -eq $property.Value) {
        throw "$Purpose is missing $Name."
    }
    return $property.Value
}

function Assert-SchemaVersionAtLeast {
    param([object] $Version, [int] $Minimum, [string] $FailureMessage)

    $value = [string] $Version
    [int]$parsedVersion = 0
    if ($value -notmatch '^[1-9][0-9]*$' -or
        -not [int]::TryParse($value, [ref]$parsedVersion) -or
        $parsedVersion -lt $Minimum) {
        throw $FailureMessage
    }
}

function Assert-Sha256 {
    param([object] $Value, [string] $FailureMessage)

    if ([string]$Value -cnotmatch '^[a-f0-9]{64}$') {
        throw $FailureMessage
    }
}

function Find-CurrentRestoreReport {
    param([string] $Source, [DateTimeOffset] $StartedAt)

    $sourceItem = Get-Item -LiteralPath $Source
    $reportPrefix = "$($sourceItem.Name).restore-"
    $reports = @(
        Get-ChildItem -LiteralPath $sourceItem.DirectoryName -File |
            Where-Object {
                $_.Name.StartsWith($reportPrefix, [System.StringComparison]::Ordinal) -and
                $_.LastWriteTimeUtc -ge $StartedAt.UtcDateTime
            }
    )
    if ($reports.Count -ne 1) {
        throw "Restore did not produce exactly one new report for this backup."
    }
    return $reports[0].FullName
}

$source = Resolve-ExistingDDriveFile -Path $BackupFile
$metadataFile = "$source.metadata.json"
if (-not (Test-Path -LiteralPath $metadataFile -PathType Leaf)) {
    throw "Backup metadata is required: $metadataFile"
}
$metadata = Get-Content -LiteralPath $metadataFile -Raw | ConvertFrom-Json -ErrorAction Stop
$recordedHash = Get-RequiredPropertyValue -Object $metadata -Name "sha256" -Purpose "Backup metadata"
Assert-Sha256 -Value $recordedHash -FailureMessage "Backup metadata checksum is invalid."
$actualHash = Get-Sha256Hex -Path $source
if ($actualHash -cne $recordedHash) {
    throw "Backup checksum mismatch; restore drill was not started."
}
$sourceVersion = Get-RequiredPropertyValue -Object $metadata -Name "flyway_schema_version" -Purpose "Backup metadata"
Assert-SchemaVersionAtLeast -Version $sourceVersion -Minimum $MinimumSchemaVersion -FailureMessage "Source backup metadata schema is below the requested minimum."

if ($Mode -eq "Validate") {
    Write-Output "RESTORE_DRILL status=PASS mode=$Mode scope=local-or-staging source_sha256=$actualHash schema_version=$sourceVersion"
    exit 0
}
if (-not $ConfirmRestoreDrill) {
    throw "Run mode requires -ConfirmRestoreDrill."
}
if ([string]::IsNullOrWhiteSpace($RestoreDrillScope)) {
    throw "Run mode requires -RestoreDrillScope local-or-staging."
}

$startedAt = [DateTimeOffset]::UtcNow
$powerShellCommand = Get-RecoveryPowerShellCommand
$restoreArguments = @(
    "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", $restoreScript,
    "-BackupFile", $source, "-RestoreDrillScope", $RestoreDrillScope,
    "-MinimumSchemaVersion", $MinimumSchemaVersion
)
if ($HostedCi) { $restoreArguments += "-HostedCi" }
& $powerShellCommand @restoreArguments
if ($LASTEXITCODE -ne 0) {
    throw "Restore failed; the target is retained for diagnosis."
}

$reportFile = Find-CurrentRestoreReport -Source $source -StartedAt $startedAt
$report = Get-Content -LiteralPath $reportFile -Raw | ConvertFrom-Json -ErrorAction Stop
if ([System.IO.Path]::GetFullPath([string](Get-RequiredPropertyValue -Object $report -Name "source_backup" -Purpose "Restore report")) -cne $source) {
    throw "Restore report source does not match the requested backup."
}
if ([string](Get-RequiredPropertyValue -Object $report -Name "source_sha256" -Purpose "Restore report") -cne $actualHash) {
    throw "Restore report checksum does not match the requested backup."
}
Assert-SchemaVersionAtLeast -Version (Get-RequiredPropertyValue -Object $report -Name "restored_flyway_schema_version" -Purpose "Restore report") -Minimum $MinimumSchemaVersion -FailureMessage "Restore report schema is below the requested minimum."
if ([string](Get-RequiredPropertyValue -Object $report -Name "role_and_rls_gate" -Purpose "Restore report") -cne "PASS") {
    throw "Restore report role and RLS gate did not pass."
}
if ([string](Get-RequiredPropertyValue -Object $report -Name "runtime_tenant_rls_smoke" -Purpose "Restore report") -cne "PASS") {
    throw "Restore report runtime tenant RLS smoke did not pass."
}
foreach ($toolVersion in @("psql_version", "pg_restore_version")) {
    if ([string]::IsNullOrWhiteSpace([string](Get-RequiredPropertyValue -Object $report -Name $toolVersion -Purpose "Restore report"))) {
        throw "Restore report $toolVersion is missing."
    }
}
if ([string](Get-RequiredPropertyValue -Object $report -Name "restore_drill_scope" -Purpose "Restore report") -cne $RestoreDrillScope) {
    throw "Restore report scope does not match the requested restore drill scope."
}
[double]$elapsedSeconds = Get-RequiredPropertyValue -Object $report -Name "elapsed_seconds" -Purpose "Restore report"
if ([double]::IsNaN($elapsedSeconds) -or [double]::IsInfinity($elapsedSeconds) -or $elapsedSeconds -lt 0) {
    throw "Restore report elapsed time is invalid."
}

Write-Output "RESTORE_DRILL status=PASS mode=$Mode scope=local-or-staging report=$reportFile source_sha256=$actualHash schema_version=$($report.restored_flyway_schema_version) elapsed_seconds=$elapsedSeconds"
