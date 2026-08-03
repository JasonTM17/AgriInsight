Set-StrictMode -Version 3.0

function Get-RecoveryPowerShellCommand {
    $isWindowsHost = [System.IO.Path]::DirectorySeparatorChar -eq '\'
    $commandName = if ($isWindowsHost) { "powershell" } else { "pwsh" }
    $command = Get-Command $commandName -ErrorAction SilentlyContinue
    if ($null -eq $command) {
        throw "$commandName is required for the recovery workflow."
    }
    return $command.Source
}

function Assert-GitHubHostedRecoveryRunner {
    if ([System.IO.Path]::DirectorySeparatorChar -eq '\' -or
            $env:GITHUB_ACTIONS -cne "true" -or
            $env:RUNNER_ENVIRONMENT -cne "github-hosted" -or
            $env:RUNNER_OS -cne "Linux" -or
            $env:CI -cne "true" -or
            [string]::IsNullOrWhiteSpace($env:RUNNER_TEMP)) {
        throw "HostedCi recovery is restricted to GitHub-hosted Linux runners."
    }

    $runnerTemp = [System.IO.Path]::GetFullPath($env:RUNNER_TEMP)
    if (-not [System.IO.Path]::IsPathRooted($runnerTemp) -or
            -not (Test-Path -LiteralPath $runnerTemp -PathType Container)) {
        throw "RUNNER_TEMP must resolve to an existing rooted hosted-runner directory."
    }
    return $runnerTemp
}

function Invoke-RecoveryDiskGuard {
    param(
        [Parameter(Mandatory = $true)]
        [string] $ProjectRoot,
        [switch] $HostedCi
    )

    $powerShellCommand = Get-RecoveryPowerShellCommand
    if ($HostedCi) {
        $runnerTemp = Assert-GitHubHostedRecoveryRunner
        $guard = Join-Path $ProjectRoot "scripts/check-hosted-ci-disk.ps1"
        $output = @(& $powerShellCommand -NoProfile -File $guard -Path $runnerTemp 2>&1)
        $exitCode = $LASTEXITCODE
        $output | Write-Output
        if ($exitCode -ne 0 -or ($output -join "`n") -notmatch "HOSTED_DISK_GUARD overall=(PASS|WARN)") {
            throw "Hosted CI disk guard is not acceptable; recovery was not started."
        }
        return
    }

    $guard = Join-Path $ProjectRoot "scripts/check-workspace-disk.ps1"
    $output = @(& $powerShellCommand -NoProfile -ExecutionPolicy Bypass -File $guard 2>&1)
    $exitCode = $LASTEXITCODE
    $output | Write-Output
    if ($exitCode -ne 0 -or ($output -join "`n") -notmatch "DISK_GUARD overall=PASS") {
        throw "Disk guard is not PASS; recovery was not started."
    }
}

Export-ModuleMember -Function Get-RecoveryPowerShellCommand, Assert-GitHubHostedRecoveryRunner, Invoke-RecoveryDiskGuard
