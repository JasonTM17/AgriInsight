[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string] $Path
)

Set-StrictMode -Version 3.0
$ErrorActionPreference = "Stop"

$resolvedPath = [IO.Path]::GetFullPath($Path)
if (-not (Test-Path -LiteralPath $resolvedPath -PathType Container)) {
    throw "Hosted CI disk path does not exist: $resolvedPath"
}

$root = [IO.Path]::GetPathRoot($resolvedPath)
$drive = [IO.DriveInfo]::new($root)
$freeGb = [double]$drive.AvailableFreeSpace / 1GB
$warnBelowGb = 10.0
$failBelowGb = 8.0
$status = if ($freeGb -lt $failBelowGb) {
    "FAIL"
} elseif ($freeGb -lt $warnBelowGb) {
    "WARN"
} else {
    "PASS"
}

Write-Output ((
    "HOSTED_DISK_GUARD path={0} free_gb={1:F3} warn_below_gb={2:F3} " +
    "fail_below_gb={3:F3} status={4} source=runner-temp"
) -f $resolvedPath, $freeGb, $warnBelowGb, $failBelowGb, $status)

if ($status -eq "FAIL") {
    Write-Output "HOSTED_DISK_GUARD overall=FAIL exit_code=2 source=runner-temp"
    exit 2
}

Write-Output (
    "HOSTED_DISK_GUARD overall={0} exit_code=0 source=runner-temp" -f $status
)
exit 0
