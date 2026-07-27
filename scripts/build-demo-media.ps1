[CmdletBinding()]
param(
    [string] $CaptureRoot = "artifacts/media-capture",
    [string] $OutputRoot = "docs/assets/screens",
    [string] $GifPath = "docs/assets/agriinsight-tour.gif",
    [int] $StillWidth = 1280,
    [int] $GifWidth = 960,
    [int] $GifDelayCentiseconds = 180
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$repositoryRoot = Split-Path -Parent $PSScriptRoot
$captureRoot = Join-Path $repositoryRoot $CaptureRoot
$screensIn = Join-Path $captureRoot "screens"
$framesIn = Join-Path $captureRoot "frames"
$screensOut = Join-Path $repositoryRoot $OutputRoot
$gifOut = Join-Path $repositoryRoot $GifPath

function Resolve-Magick {
    $command = Get-Command "magick" -ErrorAction SilentlyContinue
    if ($command) { return $command.Source }
    $fallback = Get-ChildItem "C:\Program Files" -Filter "magick.exe" -Recurse `
        -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($fallback) { return $fallback.FullName }
    throw "ImageMagick (magick.exe) is required to build documentation media"
}

$magick = Resolve-Magick
Write-Output "MEDIA_BUILD magick=$magick"

if (-not (Test-Path -LiteralPath $screensIn)) {
    throw "No captured screenshots at $screensIn. Run the guarded runner with -CaptureMedia first."
}
$captured = @(Get-ChildItem -LiteralPath $screensIn -Filter "*.png" | Sort-Object Name)
if ($captured.Count -eq 0) {
    throw "No captured screenshots found in $screensIn"
}

New-Item -ItemType Directory -Force -Path $screensOut | Out-Null
foreach ($shot in $captured) {
    $target = Join-Path $screensOut ($shot.BaseName + ".webp")
    & $magick $shot.FullName -resize "${StillWidth}x>" -strip -quality 82 $target
    if ($LASTEXITCODE -ne 0) { throw "Failed to convert $($shot.Name)" }
    $kb = [math]::Round((Get-Item -LiteralPath $target).Length / 1KB, 1)
    Write-Output ("MEDIA_STILL {0} {1} KB" -f $shot.BaseName, $kb)
}

$frames = @(Get-ChildItem -LiteralPath $framesIn -Filter "tour-*.png" -ErrorAction SilentlyContinue |
    Sort-Object Name)
if ($frames.Count -lt 2) {
    throw "The tour GIF needs at least two frames; found $($frames.Count) in $framesIn"
}
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $gifOut) | Out-Null
$framePaths = $frames | ForEach-Object { $_.FullName }
& $magick -delay $GifDelayCentiseconds -loop 0 @framePaths `
    -resize "${GifWidth}x>" -layers OptimizeTransparency -colors 200 $gifOut
if ($LASTEXITCODE -ne 0) { throw "Failed to assemble $gifOut" }

$gifMb = [math]::Round((Get-Item -LiteralPath $gifOut).Length / 1MB, 2)
Write-Output ("MEDIA_GIF frames={0} size={1} MB" -f $frames.Count, $gifMb)
if ($gifMb -gt 8) {
    throw "The tour GIF is ${gifMb} MB, which is too heavy for a repository README"
}
Write-Output "MEDIA_BUILD=PASS"
