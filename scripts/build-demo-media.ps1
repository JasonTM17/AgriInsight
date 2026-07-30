[CmdletBinding()]
param(
    [string] $CaptureRoot = "artifacts/media-capture",
    [string] $OutputRoot = "docs/assets/screens",
    [string] $GifPath = "docs/assets/agriinsight-tour.gif",
    [string] $ForecastGifPath = "assets/generated/agriinsight-inventory-forecast-loop.gif",
    [int] $StillWidth = 1280,
    [ValidateRange(1, 16000)]
    [int] $StillMaxHeight = 12000,
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
    if ($env:AGRIINSIGHT_MAGICK_PATH) {
        if (-not (Test-Path -LiteralPath $env:AGRIINSIGHT_MAGICK_PATH)) {
            throw "AGRIINSIGHT_MAGICK_PATH does not exist: $($env:AGRIINSIGHT_MAGICK_PATH)"
        }
        return $env:AGRIINSIGHT_MAGICK_PATH
    }
    $command = Get-Command "magick" -ErrorAction SilentlyContinue
    if ($command) { return $command.Source }
    $isWindowsPlatform = (
        [System.Environment]::OSVersion.Platform -eq
        [System.PlatformID]::Win32NT
    )
    if (-not $isWindowsPlatform) {
        $command = Get-Command "convert" -ErrorAction SilentlyContinue
        if ($command) { return $command.Source }
    }
    # Bounded probe of the default installer layout. A recursive sweep of
    # Program Files can take minutes, so only the one directory level is checked.
    foreach ($base in @("$env:ProgramFiles", "${env:ProgramFiles(x86)}")) {
        if (-not $base -or -not (Test-Path -LiteralPath $base)) { continue }
        $candidate = Get-ChildItem -LiteralPath $base -Directory `
            -Filter "ImageMagick*" -ErrorAction SilentlyContinue |
            ForEach-Object { Join-Path $_.FullName "magick.exe" } |
            Where-Object { Test-Path -LiteralPath $_ } |
            Select-Object -First 1
        if ($candidate) { return $candidate }
    }
    throw (
        "ImageMagick (magick or convert) is required to build documentation " +
        "media. Install it or set AGRIINSIGHT_MAGICK_PATH."
    )
}

function Build-Gif {
    param(
        [System.IO.FileInfo[]] $Frames,
        [string] $OutputPath,
        [string] $Label
    )
    $frameCount = @($Frames).Count
    if ($frameCount -lt 2) {
        throw "$Label needs at least two frames; found $frameCount"
    }
    New-Item -ItemType Directory -Force `
        -Path (Split-Path -Parent $OutputPath) | Out-Null
    $framePaths = $Frames | ForEach-Object { $_.FullName }
    & $magick -delay $GifDelayCentiseconds -loop 0 @framePaths `
        -resize "${GifWidth}x>" -layers OptimizeTransparency -colors 200 `
        $OutputPath
    if ($LASTEXITCODE -ne 0) { throw "Failed to assemble $OutputPath" }

    $gifMb = [math]::Round((Get-Item -LiteralPath $OutputPath).Length / 1MB, 2)
    Write-Output (
        "MEDIA_GIF label={0} frames={1} size={2} MB" -f
        $Label, $frameCount, $gifMb
    )
    if ($gifMb -gt 8) {
        throw "$Label is ${gifMb} MB, which is too heavy for a repository README"
    }
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
    & $magick $shot.FullName `
        -resize "${StillWidth}x${StillMaxHeight}>" `
        -strip -quality 82 $target
    if ($LASTEXITCODE -ne 0) { throw "Failed to convert $($shot.Name)" }
    $kb = [math]::Round((Get-Item -LiteralPath $target).Length / 1KB, 1)
    Write-Output ("MEDIA_STILL {0} {1} KB" -f $shot.BaseName, $kb)
}

$frames = @(Get-ChildItem -LiteralPath $framesIn -Filter "tour-*.png" -ErrorAction SilentlyContinue |
    Sort-Object Name)
$forecastGifOut = Join-Path $repositoryRoot $ForecastGifPath
$forecastFrames = @(
    Get-ChildItem -LiteralPath $framesIn -Filter "forecast-*.png" `
        -ErrorAction SilentlyContinue |
        Sort-Object Name
)
Build-Gif -Frames $frames -OutputPath $gifOut -Label "Tour GIF"
Build-Gif -Frames $forecastFrames -OutputPath $forecastGifOut `
    -Label "Inventory forecast GIF"
Write-Output "MEDIA_BUILD=PASS"
