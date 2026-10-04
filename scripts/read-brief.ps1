[CmdletBinding()]
param([ValidateRange(0, 63)][int[]]$Section)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$projectRoot = Split-Path -Parent $PSScriptRoot
$brief = Get-Content -Encoding UTF8 -Raw -LiteralPath (Join-Path $projectRoot 'docs/PROJECT_BRIEF.md')
$headings = [regex]::Matches($brief, '(?m)^# (\d+)\. [^\r\n]+')
if (-not $Section -or $Section.Count -eq 0) {
    $headings | ForEach-Object { Write-Output $_.Value }
    return
}
foreach ($sectionNumber in ($Section | Select-Object -Unique)) {
    $found = $false
    for ($index = 0; $index -lt $headings.Count; $index++) {
        if ([int]$headings[$index].Groups[1].Value -ne $sectionNumber) { continue }
        $start = $headings[$index].Index
        $end = $brief.Length
        if ($index + 1 -lt $headings.Count) { $end = $headings[$index + 1].Index }
        Write-Output $brief.Substring($start, $end - $start).TrimEnd()
        $found = $true
        break
    }
    if (-not $found) { throw "Brief section $sectionNumber was not found" }
}
