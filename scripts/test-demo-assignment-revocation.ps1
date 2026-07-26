[CmdletBinding()]
param(
    [string] $DatabaseHost = "127.0.0.1",
    [ValidateRange(1, 65535)]
    [int] $DatabasePort = 5432,
    [string] $DatabaseName = "agriinsight_demo",
    [string] $DatabaseUser = "agriinsight_migrator",
    [string] $OutputDirectory = "_tmp/demo-bootstrap"
)

Set-StrictMode -Version 3.0
$ErrorActionPreference = "Stop"

if ($DatabaseHost -notin @("127.0.0.1", "localhost", "::1")) {
    throw "Demo lifecycle probe only accepts a loopback database host."
}
if ($DatabaseName -ne "agriinsight_demo") {
    throw "Demo lifecycle probe only accepts the agriinsight_demo database."
}
if ([string]::IsNullOrWhiteSpace($env:PGPASSWORD)) {
    throw "Set PGPASSWORD for the demo migrator without writing it to disk."
}

$repositoryRoot = Split-Path -Parent $PSScriptRoot
$contract = Get-Content -LiteralPath (
    Join-Path $repositoryRoot "deploy/demo/demo-tenant.json"
) -Raw | ConvertFrom-Json
$demoTenantId = [Guid]::Parse([string]$contract.tenant.id).ToString()
$psql = (Get-Command psql -ErrorAction Stop).Source
$connectionArguments = @(
    "-X",
    "--no-psqlrc",
    "--host", $DatabaseHost,
    "--port", "$DatabasePort",
    "--dbname", $DatabaseName,
    "--username", $DatabaseUser,
    "--set", "ON_ERROR_STOP=1",
    "--quiet",
    "--tuples-only",
    "--no-align"
)

$revocationSql = @"
BEGIN;
SET LOCAL app.tenant_id = '$demoTenantId';
WITH target AS (
    SELECT assignment.id
    FROM activity_assignees AS assignment
    JOIN employees AS employee
      ON employee.tenant_id = assignment.tenant_id
     AND employee.id = assignment.employee_id
    WHERE employee.code = 'DEMO-FIELD-WORKER'
      AND assignment.revoked_at IS NULL
    ORDER BY assignment.activity_id, assignment.id
    LIMIT 1
)
UPDATE activity_assignees AS assignment
SET revoked_at = CURRENT_TIMESTAMP,
    version = assignment.version + 1,
    updated_at = CURRENT_TIMESTAMP
FROM target
WHERE assignment.id = target.id
RETURNING concat_ws(
    '|',
    assignment.id,
    assignment.tenant_id,
    assignment.activity_id,
    assignment.employee_id
);
COMMIT;
"@

$revocationOutput = @(
    & $psql @connectionArguments --command $revocationSql
)
$revocationExitCode = $LASTEXITCODE
$revokedRows = @(
    $revocationOutput |
        ForEach-Object { ([string]$_).Trim() } |
        Where-Object {
            $_ -match (
                "^[0-9a-fA-F-]{36}\|[0-9a-fA-F-]{36}\|" +
                "[0-9a-fA-F-]{36}\|[0-9a-fA-F-]{36}$"
            )
        }
)
if ($revocationExitCode -ne 0 -or $revokedRows.Count -ne 1) {
    throw "Expected exactly one active demo assignment to revoke."
}

$parts = ([string]$revokedRows[0]).Trim().Split("|")
if ($parts.Count -ne 4) {
    throw "Demo assignment revocation returned an invalid identity."
}
foreach ($part in $parts) {
    [void][Guid]::Parse($part)
}
$assignmentId, $tenantId, $activityId, $employeeId = $parts

& powershell -ExecutionPolicy Bypass `
    -File (Join-Path $PSScriptRoot "bootstrap-demo-environment.ps1") `
    -ConfirmLocalDemo `
    -DatabaseHost $DatabaseHost `
    -DatabasePort $DatabasePort `
    -DatabaseName $DatabaseName `
    -DatabaseUser $DatabaseUser `
    -OutputDirectory $OutputDirectory
if ($LASTEXITCODE -ne 0) {
    throw "Demo bootstrap failed after an assignment revocation."
}

$verificationSql = @"
BEGIN;
SET LOCAL app.tenant_id = '$demoTenantId';
SELECT concat_ws(
    '|',
    count(*) FILTER (
        WHERE id = '$assignmentId'::uuid
          AND revoked_at IS NOT NULL
    ),
    count(*) FILTER (
        WHERE tenant_id = '$tenantId'::uuid
          AND activity_id = '$activityId'::uuid
          AND employee_id = '$employeeId'::uuid
          AND revoked_at IS NULL
    ),
    count(*) FILTER (
        WHERE tenant_id = '$tenantId'::uuid
          AND activity_id = '$activityId'::uuid
          AND employee_id = '$employeeId'::uuid
    )
)
FROM activity_assignees;
COMMIT;
"@

$verificationOutput = @(
    & $psql @connectionArguments --command $verificationSql
)
$verificationExitCode = $LASTEXITCODE
$states = @(
    $verificationOutput |
        ForEach-Object { ([string]$_).Trim() } |
        Where-Object { $_ -match "^\d+\|\d+\|\d+$" }
)
if (
    $verificationExitCode -ne 0 -or
    $states.Count -ne 1 -or
    $states[0] -ne "1|0|1"
) {
    throw (
        "Revoked demo assignment history was not preserved fail-closed: {0}" -f
            ($states -join ",")
    )
}

Write-Output (
    "DEMO_ASSIGNMENT_REVOCATION=PASS preserved=1 active=0 history=1"
)
