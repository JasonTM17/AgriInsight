[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
$workspace = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$output = Join-Path $workspace "artifacts\big-data"
$pythonTemp = Join-Path $workspace "tmp\python-big-data"
New-Item -ItemType Directory -Force -Path $pythonTemp | Out-Null

$env:TEMP = $pythonTemp
$env:TMP = $pythonTemp
$env:PYTHONPYCACHEPREFIX = Join-Path $workspace "tmp\python-cache"
$sourceRoot = Join-Path $workspace "src"
$env:PYTHONPATH = if ($env:PYTHONPATH) {
    "$sourceRoot$([IO.Path]::PathSeparator)$env:PYTHONPATH"
} else {
    $sourceRoot
}

& powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $workspace "scripts\check-workspace-disk.ps1")
if ($LASTEXITCODE -ne 0) { throw "Disk guard rejected the big-data run before execution." }

Push-Location $workspace
try {
    python -m agriinsight run --profile big-data --output $output
    if ($LASTEXITCODE -ne 0) { throw "Big-data pipeline failed." }
}
finally {
    Pop-Location
}

& powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $workspace "scripts\check-workspace-disk.ps1")
if ($LASTEXITCODE -ne 0) { throw "Disk guard rejected the workspace after the big-data run." }

Write-Output "Big-data artifacts: $output"
