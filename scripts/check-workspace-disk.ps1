[CmdletBinding()]
param(
    [switch]$TestOnly,
    [Nullable[double]]$TestOnlyCFreeGb,
    [Nullable[double]]$TestOnlyDFreeGb,
    [ValidateSet("C", "D")]
    [string]$TestOnlyMissingDrive
)

Set-StrictMode -Version 2.0
$ErrorActionPreference = "Stop"

$drivePolicies = @(
    [pscustomobject]@{
        Letter = "C"
        WarnBelowGb = 10.0
        FailBelowGb = 8.0
    },
    [pscustomobject]@{
        Letter = "D"
        WarnBelowGb = 25.0
        FailBelowGb = 20.0
    }
)

function Write-ConfigurationFailure {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Reason
    )

    Write-Output (
        "DISK_GUARD overall=FAIL exit_code=2 source=configuration reason={0}" -f $Reason
    )
}

function Test-FreeSpaceValue {
    param(
        [Parameter(Mandatory = $true)]
        [double]$Value
    )

    return (
        $Value -ge 0.0 -and
        -not [double]::IsNaN($Value) -and
        -not [double]::IsInfinity($Value)
    )
}

function Get-ActualDriveObservation {
    param(
        [Parameter(Mandatory = $true)]
        [string]$DriveLetter
    )

    try {
        $drives = @(
            Get-PSDrive `
                -Name $DriveLetter `
                -PSProvider FileSystem `
                -ErrorAction SilentlyContinue
        )
    }
    catch {
        return [pscustomobject]@{
            Available = $false
            FreeGb = $null
            Reason = "observation-failed"
        }
    }

    if ($drives.Count -eq 0) {
        return [pscustomobject]@{
            Available = $false
            FreeGb = $null
            Reason = "drive-not-found"
        }
    }

    $freeBytes = $drives[0].Free
    if ($null -eq $freeBytes) {
        return [pscustomobject]@{
            Available = $false
            FreeGb = $null
            Reason = "free-space-unavailable"
        }
    }

    $freeGb = [double]$freeBytes / 1GB
    if (-not (Test-FreeSpaceValue -Value $freeGb)) {
        return [pscustomobject]@{
            Available = $false
            FreeGb = $null
            Reason = "invalid-free-space-reading"
        }
    }

    return [pscustomobject]@{
        Available = $true
        FreeGb = $freeGb
        Reason = $null
    }
}

function Get-DriveStatus {
    param(
        [Parameter(Mandatory = $true)]
        [pscustomobject]$Observation,
        [Parameter(Mandatory = $true)]
        [pscustomobject]$Policy
    )

    if (-not $Observation.Available) {
        return "FAIL"
    }
    if ($Observation.FreeGb -lt $Policy.FailBelowGb) {
        return "FAIL"
    }
    if ($Observation.FreeGb -lt $Policy.WarnBelowGb) {
        return "WARN"
    }
    return "PASS"
}

$testParameterNames = @(
    "TestOnlyCFreeGb",
    "TestOnlyDFreeGb",
    "TestOnlyMissingDrive"
)
$hasTestParameters = $false
foreach ($parameterName in $testParameterNames) {
    if ($PSBoundParameters.ContainsKey($parameterName)) {
        $hasTestParameters = $true
    }
}

if (-not $TestOnly -and $hasTestParameters) {
    Write-ConfigurationFailure -Reason "test-only-switch-required"
    exit 2
}

if ($TestOnly) {
    foreach ($policy in $drivePolicies) {
        $valueParameterName = "TestOnly{0}FreeGb" -f $policy.Letter
        $isMissing = $TestOnlyMissingDrive -eq $policy.Letter
        $hasValue = $PSBoundParameters.ContainsKey($valueParameterName)

        if ($isMissing -and $hasValue) {
            Write-ConfigurationFailure -Reason (
                "conflicting-test-observation-{0}" -f $policy.Letter
            )
            exit 2
        }
        if (-not $isMissing -and -not $hasValue) {
            Write-ConfigurationFailure -Reason (
                "missing-test-observation-{0}" -f $policy.Letter
            )
            exit 2
        }
        if ($hasValue) {
            $value = [double]$PSBoundParameters[$valueParameterName]
            if (-not (Test-FreeSpaceValue -Value $value)) {
                Write-ConfigurationFailure -Reason (
                    "invalid-test-observation-{0}" -f $policy.Letter
                )
                exit 2
            }
        }
    }
    $observationSource = "test-only"
}
else {
    $observationSource = "actual"
}

$statuses = @()
$invariantCulture = [System.Globalization.CultureInfo]::InvariantCulture

foreach ($policy in $drivePolicies) {
    if ($TestOnly) {
        if ($TestOnlyMissingDrive -eq $policy.Letter) {
            $observation = [pscustomobject]@{
                Available = $false
                FreeGb = $null
                Reason = "drive-not-found"
            }
        }
        else {
            $valueParameterName = "TestOnly{0}FreeGb" -f $policy.Letter
            $observation = [pscustomobject]@{
                Available = $true
                FreeGb = [double]$PSBoundParameters[$valueParameterName]
                Reason = $null
            }
        }
    }
    else {
        $observation = Get-ActualDriveObservation -DriveLetter $policy.Letter
    }

    $status = Get-DriveStatus -Observation $observation -Policy $policy
    $statuses += $status
    $warnText = $policy.WarnBelowGb.ToString("F3", $invariantCulture)
    $failText = $policy.FailBelowGb.ToString("F3", $invariantCulture)

    if ($observation.Available) {
        $freeText = $observation.FreeGb.ToString("F3", $invariantCulture)
        Write-Output (
            "DISK_GUARD drive={0} free_gb={1} warn_below_gb={2} fail_below_gb={3} status={4} source={5}" -f `
                $policy.Letter,
                $freeText,
                $warnText,
                $failText,
                $status,
                $observationSource
        )
    }
    else {
        Write-Output (
            "DISK_GUARD drive={0} free_gb=unavailable warn_below_gb={1} fail_below_gb={2} status=FAIL source={3} reason={4}" -f `
                $policy.Letter,
                $warnText,
                $failText,
                $observationSource,
                $observation.Reason
        )
    }
}

if ($statuses -contains "FAIL") {
    Write-Output (
        "DISK_GUARD overall=FAIL exit_code=2 source={0}" -f $observationSource
    )
    exit 2
}
if ($statuses -contains "WARN") {
    Write-Output (
        "DISK_GUARD overall=WARN exit_code=0 source={0}" -f $observationSource
    )
    exit 0
}

Write-Output (
    "DISK_GUARD overall=PASS exit_code=0 source={0}" -f $observationSource
)
exit 0
