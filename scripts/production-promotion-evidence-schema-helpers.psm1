Set-StrictMode -Version 3.0
$ErrorActionPreference = "Stop"

$script:PlaceholderPattern = '^\s*(?:required|todo|tbd|<[^>]+>)\s*$'
$script:UnresolvedApprovalMarkers = @(
    "pending", "unknown", "nogo", "unassigned", "unassignednogo"
)

function Get-RequiredProperty {
    param([object] $Object, [string] $Name, [string] $Path)

    $property = $Object.PSObject.Properties[$Name]
    if ($null -eq $property -or $null -eq $property.Value) {
        throw "$Path is required."
    }
    if ($property.Name -cne $Name) {
        throw "$Path must use the canonical property name."
    }
    return $property.Value
}

function Get-RequiredObject {
    param([object] $Object, [string] $Name, [string] $Path)

    $value = Get-RequiredProperty -Object $Object -Name $Name -Path $Path
    if ($value -is [string] -or $null -eq $value.PSObject) {
        throw "$Path must be an object."
    }
    return $value
}

function Get-RequiredString {
    param([object] $Object, [string] $Name, [string] $Path)

    $value = Get-RequiredProperty -Object $Object -Name $Name -Path $Path
    if ($value -isnot [string]) {
        throw "$Path must be a non-empty string."
    }
    $trimmed = $value.Trim()
    if ([string]::IsNullOrWhiteSpace($trimmed) -or $trimmed -match $script:PlaceholderPattern) {
        throw "$Path is required."
    }
    return $trimmed
}

function Get-RequiredApprovalString {
    param([object] $Object, [string] $Name, [string] $Path)

    $value = Get-RequiredString -Object $Object -Name $Name -Path $Path
    $normalized = $value.Normalize([System.Text.NormalizationForm]::FormKC).ToLowerInvariant()
    $compact = [regex]::Replace($normalized, '[\p{P}\p{S}\p{Z}\p{C}\s]+', '')
    if ($compact -in $script:UnresolvedApprovalMarkers) {
        throw "$Path is required."
    }
    return $value
}

function Assert-Pattern {
    param([string] $Value, [string] $Pattern, [string] $Name, [string] $Description)

    if ($Value -notmatch $Pattern) {
        throw "$Name must be $Description."
    }
}

function Assert-HttpsReference {
    param([string] $Value, [string] $Name)

    $uri = $null
    if (-not [Uri]::TryCreate($Value, [UriKind]::Absolute, [ref] $uri) -or $uri.Scheme -ne "https") {
        throw "$Name must be an HTTPS reference."
    }
}

function ConvertTo-RequiredTimestamp {
    param([object] $Value, [string] $Name)

    if ($Value -is [DateTimeOffset]) {
        return $Value
    }
    if ($Value -is [DateTime]) {
        return [DateTimeOffset] $Value
    }
    if ($Value -isnot [string] -or [string]::IsNullOrWhiteSpace($Value.Trim()) -or $Value -match $script:PlaceholderPattern) {
        throw "$Name must be an ISO-8601 timestamp."
    }
    try {
        return [DateTimeOffset]::Parse(
            $Value.Trim(),
            [System.Globalization.CultureInfo]::InvariantCulture,
            [System.Globalization.DateTimeStyles]::RoundtripKind
        )
    }
    catch {
        throw "$Name must be an ISO-8601 timestamp."
    }
}

function Assert-ApprovalTimestampEncoding {
    param([string] $Json)

    $matches = [regex]::Matches($Json, '"((?:\\.|[^"])*)"\s*:\s*"((?:\\.|[^"])*)"')
    foreach ($match in $matches) {
        $rawName = $match.Groups[1].Value
        $decodedName = ('"' + $rawName + '"') | ConvertFrom-Json -ErrorAction Stop
        if ($decodedName -ieq "approved_at_utc" -or $decodedName -ieq "due_at_utc") {
            if ($decodedName -cnotin @("approved_at_utc", "due_at_utc") -or
                $rawName -cne $decodedName -or
                $match.Groups[2].Value -notmatch 'Z$') {
                throw "Approval timestamps must use canonical UTC Z notation."
            }
        }
    }
}

Export-ModuleMember -Function Get-RequiredProperty, Get-RequiredObject, Get-RequiredString, Get-RequiredApprovalString, Assert-Pattern, Assert-HttpsReference, ConvertTo-RequiredTimestamp, Assert-ApprovalTimestampEncoding
