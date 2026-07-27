[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string] $Image,

    [Parameter(Mandatory = $true)]
    [ValidateSet("python", "backend", "web", "analytics-api")]
    [string] $Kind
)

Set-StrictMode -Version 3.0
$ErrorActionPreference = "Stop"

function Invoke-Docker {
    param(
        [Parameter(Mandatory = $true)]
        [string[]] $Arguments
    )

    & docker @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "docker $($Arguments[0]) failed with exit code $LASTEXITCODE"
    }
}

$configuredUser = (& docker image inspect $Image --format "{{.Config.User}}").Trim()
if ($LASTEXITCODE -ne 0) {
    throw "Unable to inspect image: $Image"
}
if (
    [string]::IsNullOrWhiteSpace($configuredUser) -or
    $configuredUser -in @("0", "0:0", "root", "root:root")
) {
    throw "Image must configure a non-root runtime user: $Image"
}

if ($Kind -eq "python") {
    Invoke-Docker -Arguments @(
        "run", "--rm", "--read-only", "--tmpfs", "/tmp:rw,noexec,nosuid,size=64m",
        "--cap-drop", "ALL", "--security-opt", "no-new-privileges",
        "--entrypoint", "python", $Image, "-c",
        "import agriinsight; print('agriinsight-python-ok')"
    )
    exit 0
}

if ($Kind -eq "backend") {
    Invoke-Docker -Arguments @(
        "run", "--rm", "--read-only", "--tmpfs", "/tmp:rw,noexec,nosuid,size=64m",
        "--cap-drop", "ALL", "--security-opt", "no-new-privileges",
        "--entrypoint", "/bin/sh", $Image, "-ec",
        "test -s /app/app.jar && java -version"
    )
    exit 0
}

$arguments = @(
    "run", "--detach", "--read-only",
    "--tmpfs", "/tmp:rw,noexec,nosuid,size=64m",
    "--cap-drop", "ALL",
    "--security-opt", "no-new-privileges"
)
if ($Kind -eq "analytics-api") {
    $arguments += @(
        "--env", "AGRIINSIGHT_ANALYTICS_ARTIFACT_ROOT=/data",
        "--env", "AGRIINSIGHT_ANALYTICS_DEMO_TENANT_ID=10000000-0000-4000-8000-000000000001",
        "--env", "AGRIINSIGHT_ANALYTICS_RECONCILIATION_REPORT=/data/reconciliation.json",
        "--env", "AGRIINSIGHT_ANALYTICS_SPRING_BASE_URL=http://127.0.0.1:8080"
    )
}
$arguments += $Image

$containerId = (& docker @arguments).Trim()
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($containerId)) {
    throw "Unable to start image for smoke test: $Image"
}

try {
    $probe = if ($Kind -eq "web") {
        @(
            "exec", $containerId, "node", "-e",
            "fetch('http://127.0.0.1:3100/api/health/live').then(r=>process.exit(r.status===200?0:1)).catch(()=>process.exit(1))"
        )
    }
    else {
        @(
            "exec", $containerId, "python", "-c",
            "import sys,urllib.request;sys.exit(0 if urllib.request.urlopen('http://127.0.0.1:8081/health/live',timeout=2).status==200 else 1)"
        )
    }

    $healthy = $false
    for ($attempt = 1; $attempt -le 30; $attempt += 1) {
        & docker @probe 2>$null
        if ($LASTEXITCODE -eq 0) {
            $healthy = $true
            break
        }
        Start-Sleep -Seconds 1
    }
    if (-not $healthy) {
        & docker logs $containerId
        throw "Liveness probe failed for $Kind image: $Image"
    }
    Write-Output (
        "IMAGE_SMOKE kind={0} image={1} user={2} read_only=true status=PASS" -f
        $Kind, $Image, $configuredUser
    )
}
finally {
    & docker rm --force $containerId *> $null
}
