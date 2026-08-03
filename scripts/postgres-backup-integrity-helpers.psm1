Set-StrictMode -Version 3.0

function Get-Sha256Hex {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Path
    )

    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "File does not exist: $Path"
    }

    $stream = [System.IO.File]::Open(
        $Path,
        [System.IO.FileMode]::Open,
        [System.IO.FileAccess]::Read,
        [System.IO.FileShare]::Read
    )
    $sha256 = [System.Security.Cryptography.SHA256]::Create()
    try {
        return ([System.BitConverter]::ToString($sha256.ComputeHash($stream))).Replace("-", "").ToLowerInvariant()
    }
    finally {
        $sha256.Dispose()
        $stream.Dispose()
    }
}

function Open-ReadLockedFileStream {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Path
    )

    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "File does not exist: $Path"
    }

    return [System.IO.File]::Open(
        $Path,
        [System.IO.FileMode]::Open,
        [System.IO.FileAccess]::Read,
        [System.IO.FileShare]::Read
    )
}

function Assert-DDrivePathWithoutReparsePoints {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Path
    )

    $resolved = [System.IO.Path]::GetFullPath($Path)
    $isWindows = [System.IO.Path]::DirectorySeparatorChar -eq '\'
    if ($isWindows) {
        $approvedRoot = "D:\"
        if ([System.IO.Path]::GetPathRoot($resolved) -ine $approvedRoot) {
            throw "Path must resolve to the D drive; received $resolved."
        }
    }
    else {
        $configuredRoot = [Environment]::GetEnvironmentVariable("AGRIINSIGHT_RECOVERY_ALLOWED_ROOT")
        if ([string]::IsNullOrWhiteSpace($configuredRoot) -or
            -not [System.IO.Path]::IsPathRooted($configuredRoot)) {
            throw "AGRIINSIGHT_RECOVERY_ALLOWED_ROOT must be an absolute path on non-Windows hosts."
        }

        $approvedRoot = [System.IO.Path]::GetFullPath($configuredRoot)
        if (-not (Test-Path -LiteralPath $approvedRoot -PathType Container)) {
            throw "AGRIINSIGHT_RECOVERY_ALLOWED_ROOT must identify an existing directory."
        }
        $approvedPrefix = $approvedRoot.TrimEnd(
            [System.IO.Path]::DirectorySeparatorChar,
            [System.IO.Path]::AltDirectorySeparatorChar
        ) + [System.IO.Path]::DirectorySeparatorChar
        if (-not $resolved.StartsWith($approvedPrefix, [System.StringComparison]::Ordinal)) {
            throw "Path must resolve inside AGRIINSIGHT_RECOVERY_ALLOWED_ROOT; received $resolved."
        }
    }

    $current = $approvedRoot
    $relative = $resolved.Substring($approvedRoot.Length).TrimStart(
        [System.IO.Path]::DirectorySeparatorChar,
        [System.IO.Path]::AltDirectorySeparatorChar
    )
    $rootItem = Get-Item -LiteralPath $approvedRoot -Force
    if (($rootItem.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0) {
        throw "Path must not traverse a symbolic link or junction: $approvedRoot"
    }
    foreach ($segment in $relative.Split([System.IO.Path]::DirectorySeparatorChar, [System.StringSplitOptions]::RemoveEmptyEntries)) {
        $current = Join-Path $current $segment
        if (-not (Test-Path -LiteralPath $current)) {
            break
        }
        $item = Get-Item -LiteralPath $current -Force
        if (($item.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0) {
            throw "Path must not traverse a symbolic link or junction: $current"
        }
    }

    return $resolved
}

function Open-ExclusiveRestoreTargetMutex {
    param(
        [Parameter(Mandatory = $true)]
        [string] $EndpointHost,
        [Parameter(Mandatory = $true)]
        [string] $Port,
        [Parameter(Mandatory = $true)]
        [string] $DatabaseName
    )

    $identity = "${EndpointHost}:${Port}/${DatabaseName}"
    $identityBytes = [System.Text.Encoding]::UTF8.GetBytes($identity)
    $sha256 = [System.Security.Cryptography.SHA256]::Create()
    try {
        $identityHash = ([System.BitConverter]::ToString($sha256.ComputeHash($identityBytes))).Replace("-", "")
    }
    finally {
        $sha256.Dispose()
    }

    $mutex = [System.Threading.Mutex]::new($false, "Global\AgriInsightRestoreDrill_$identityHash")
    try {
        $acquired = $mutex.WaitOne(0)
    }
    catch [System.Threading.AbandonedMutexException] {
        $acquired = $true
    }
    if (-not $acquired) {
        $mutex.Dispose()
        throw "Restore drill is already in progress for the requested endpoint and target database."
    }
    return $mutex
}

function New-AdjacentTemporaryPath {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Destination
    )

    $directory = Split-Path -Parent $Destination
    $leafName = Split-Path -Leaf $Destination
    if ([string]::IsNullOrWhiteSpace($directory) -or -not (Test-Path -LiteralPath $directory -PathType Container)) {
        throw "Destination directory does not exist: $directory"
    }

    for ($attempt = 1; $attempt -le 10; $attempt++) {
        $candidate = Join-Path $directory "$leafName.pending-$([Guid]::NewGuid().ToString('N'))"
        try {
            $handle = [System.IO.File]::Open(
                $candidate,
                [System.IO.FileMode]::CreateNew,
                [System.IO.FileAccess]::Write,
                [System.IO.FileShare]::None
            )
            $handle.Dispose()
            return $candidate
        }
        catch [System.IO.IOException] {
            continue
        }
    }

    throw "Could not reserve a unique temporary file beside $Destination."
}

function Publish-NewFile {
    param(
        [Parameter(Mandatory = $true)]
        [string] $TemporaryPath,
        [Parameter(Mandatory = $true)]
        [string] $Destination
    )

    if (-not (Test-Path -LiteralPath $TemporaryPath -PathType Leaf)) {
        throw "Temporary file does not exist: $TemporaryPath"
    }
    try {
        [System.IO.File]::Move($TemporaryPath, $Destination)
    }
    catch [System.IO.IOException] {
        throw "Refusing to overwrite an existing output: $Destination"
    }
}

Export-ModuleMember -Function Get-Sha256Hex, Open-ReadLockedFileStream, Assert-DDrivePathWithoutReparsePoints, Open-ExclusiveRestoreTargetMutex, New-AdjacentTemporaryPath, Publish-NewFile
