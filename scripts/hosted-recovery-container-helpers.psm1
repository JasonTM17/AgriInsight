Set-StrictMode -Version 3.0

function Invoke-CheckedDocker {
    param(
        [Parameter(Mandatory = $true)] [string[]] $Arguments,
        [Parameter(Mandatory = $true)] [string] $FailureMessage
    )

    $output = @(& docker @Arguments)
    if ($LASTEXITCODE -ne 0) {
        throw $FailureMessage
    }
    return $output
}

function Start-OwnedPostgresContainer {
    param(
        [Parameter(Mandatory = $true)] [string] $Name,
        [Parameter(Mandatory = $true)] [string] $Database,
        [Parameter(Mandatory = $true)] [string] $EnvironmentFile,
        [Parameter(Mandatory = $true)] [string] $Image,
        [Parameter(Mandatory = $true)] [string] $RunId
    )

    $output = Invoke-CheckedDocker -Arguments @(
        "run", "--detach", "--rm",
        "--name", $Name,
        "--label", "com.agriinsight.owner=hosted-recovery-drill",
        "--label", "com.agriinsight.run-id=$RunId",
        "--env-file", $EnvironmentFile,
        "--publish", "127.0.0.1::5432",
        "--health-cmd", "pg_isready -U postgres -d $Database",
        "--health-interval", "2s",
        "--health-timeout", "2s",
        "--health-retries", "30",
        $Image
    ) -FailureMessage "Could not start an owned PostgreSQL recovery container."
    $containerId = ($output -join "`n").Trim()
    if ($containerId -cnotmatch '^[a-f0-9]{64}$') {
        throw "Docker did not return a canonical owned container identifier."
    }
    return $containerId
}

function Wait-OwnedPostgresContainer {
    param([Parameter(Mandatory = $true)] [string] $ContainerId)

    for ($attempt = 1; $attempt -le 60; $attempt++) {
        $status = ((Invoke-CheckedDocker -Arguments @(
            "inspect", "--format", "{{.State.Health.Status}}", $ContainerId
        ) -FailureMessage "Could not inspect an owned PostgreSQL recovery container.") -join "`n").Trim()
        if ($status -ceq "healthy") { return }
        if ($status -ceq "unhealthy") {
            throw "Owned PostgreSQL recovery container became unhealthy."
        }
        Start-Sleep -Seconds 1
    }
    throw "Owned PostgreSQL recovery container did not become healthy."
}

function Get-OwnedPostgresPort {
    param([Parameter(Mandatory = $true)] [string] $ContainerId)

    $binding = ((Invoke-CheckedDocker -Arguments @(
        "port", $ContainerId, "5432/tcp"
    ) -FailureMessage "Could not inspect an owned PostgreSQL recovery port.") -join "`n").Trim()
    if ($binding -cnotmatch '^127\.0\.0\.1:([1-9][0-9]{0,4})$') {
        throw "Owned PostgreSQL recovery port is not bound to literal IPv4 loopback."
    }
    $port = [int] $Matches[1]
    if ($port -gt 65535) {
        throw "Owned PostgreSQL recovery port is invalid."
    }
    return $port.ToString([System.Globalization.CultureInfo]::InvariantCulture)
}

function Write-PostgresClientShims {
    param(
        [Parameter(Mandatory = $true)] [string] $Directory,
        [Parameter(Mandatory = $true)] [string] $Image,
        [Parameter(Mandatory = $true)] [string] $RecoveryRoot,
        [Parameter(Mandatory = $true)] [string] $RepositoryRoot
    )

    New-Item -ItemType Directory -Force -Path $Directory | Out-Null
    foreach ($client in @("psql", "pg_dump", "pg_restore")) {
        $shimPath = Join-Path $Directory $client
        @"
#!/bin/sh
set -eu
exec docker run --rm --interactive --network host --env PGPASSWORD \
  --volume '${RecoveryRoot}:${RecoveryRoot}' \
  --volume '${RepositoryRoot}:${RepositoryRoot}:ro' \
  '$Image' $client "`$@"
"@ | Set-Content -LiteralPath $shimPath -Encoding utf8NoBOM
        & chmod 700 $shimPath
        if ($LASTEXITCODE -ne 0) {
            throw "Could not make a PostgreSQL client shim executable."
        }
    }
}

function Remove-OwnedRecoveryContainers {
    param(
        [Parameter(Mandatory = $true)] [string[]] $ContainerIds,
        [Parameter(Mandatory = $true)] [string] $RunId
    )

    $cleanupErrors = [System.Collections.Generic.List[string]]::new()
    foreach ($containerId in $ContainerIds) {
        $ownership = @(& docker inspect --format '{{index .Config.Labels "com.agriinsight.owner"}}|{{index .Config.Labels "com.agriinsight.run-id"}}' $containerId 2>&1)
        if ($LASTEXITCODE -ne 0) {
            $remaining = @(& docker ps --all --no-trunc --filter "id=$containerId" --format '{{.ID}}' 2>&1)
            if ($LASTEXITCODE -ne 0) {
                $cleanupErrors.Add("Could not determine whether recovery container $containerId still exists.")
            }
            elseif (($remaining -join "`n").Trim() -ne "") {
                $cleanupErrors.Add("Recovery container $containerId still exists but ownership inspection failed.")
            }
            continue
        }

        if (($ownership -join "`n").Trim() -cne "hosted-recovery-drill|$RunId") {
            $cleanupErrors.Add("Refusing to remove recovery container $containerId because its ownership labels do not match this run.")
            continue
        }

        & docker rm --force $containerId | Out-Null
        if ($LASTEXITCODE -ne 0) {
            $cleanupErrors.Add("Could not remove owned recovery container $containerId.")
            continue
        }

        $remaining = @(& docker ps --all --no-trunc --filter "id=$containerId" --format '{{.ID}}' 2>&1)
        if ($LASTEXITCODE -ne 0 -or ($remaining -join "`n").Trim() -ne "") {
            $cleanupErrors.Add("Could not verify removal of owned recovery container $containerId.")
        }
    }

    if ($cleanupErrors.Count -gt 0) {
        throw ($cleanupErrors -join " ")
    }
}

Export-ModuleMember -Function Start-OwnedPostgresContainer, Wait-OwnedPostgresContainer, Get-OwnedPostgresPort, Write-PostgresClientShims, Remove-OwnedRecoveryContainers
