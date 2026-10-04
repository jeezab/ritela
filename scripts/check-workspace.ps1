$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$projectRoot = Split-Path -Parent $PSScriptRoot
$failures = [System.Collections.Generic.List[string]]::new()

# Parse without executing scripts, including bootstrap (which downloads binaries).
foreach ($script in Get-ChildItem -LiteralPath (Join-Path $projectRoot 'scripts') -Filter '*.ps1') {
    $parseTokens = $null
    $parseErrors = $null
    [void][System.Management.Automation.Language.Parser]::ParseFile($script.FullName, [ref]$parseTokens, [ref]$parseErrors)
    foreach ($parseError in $parseErrors) { $failures.Add("$($script.Name): $($parseError.Message)") }
}

$documents = @(Get-ChildItem -LiteralPath $projectRoot -Filter '*.md')
$documents += @(Get-ChildItem -LiteralPath (Join-Path $projectRoot 'docs') -Filter '*.md' -Recurse)
$documents += @(Get-ChildItem -LiteralPath (Join-Path $projectRoot '.agents/skills') -Filter 'SKILL.md' -Recurse)
foreach ($document in $documents) {
    $content = Get-Content -Encoding UTF8 -Raw -LiteralPath $document.FullName
    foreach ($match in [regex]::Matches($content, '\[[^\]]+\]\(([^)]+)\)')) {
        $target = $match.Groups[1].Value
        if ($target -match '^(https?://|mailto:|#)') { continue }
        $target = ($target -split '#', 2)[0]
        $targetPath = Join-Path $document.DirectoryName $target
        if (-not (Test-Path -LiteralPath $targetPath)) { $failures.Add("$($document.Name): broken link $target") }
    }
}

$lock = Get-Content -Encoding UTF8 -Raw -LiteralPath (Join-Path $projectRoot '.agent-skills.lock') | ConvertFrom-Json
$seenNames = @{}
foreach ($skill in $lock.skills) {
    foreach ($field in @('name', 'source', 'commit', 'path', 'license', 'version', 'sha256', 'installedAt', 'classification')) {
        if ($skill.PSObject.Properties.Name -notcontains $field) { $failures.Add("Missing field $field in skill entry") }
    }
    if ($seenNames.ContainsKey($skill.name)) { $failures.Add("Duplicate skill $($skill.name)") }
    $seenNames[$skill.name] = $true
    $skillPath = [IO.Path]::GetFullPath((Join-Path $projectRoot $skill.path))
    $skillsRoot = [IO.Path]::GetFullPath((Join-Path $projectRoot '.agents/skills')) + [IO.Path]::DirectorySeparatorChar
    if (-not $skillPath.StartsWith($skillsRoot, [StringComparison]::OrdinalIgnoreCase)) {
        $failures.Add("Skill path outside project skills: $($skill.path)")
        continue
    }
    if (-not (Test-Path -LiteralPath $skillPath -PathType Leaf)) { $failures.Add("Missing skill $($skill.path)"); continue }
    $hash = (Get-FileHash -LiteralPath $skillPath -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($hash -ne $skill.sha256) { $failures.Add("Skill hash mismatch: $($skill.name)") }
    $skillText = Get-Content -Encoding UTF8 -Raw -LiteralPath $skillPath
    if ($skillText -notmatch '(?s)^---\r?\n.*?\r?\n---\r?\n') { $failures.Add("Missing frontmatter: $($skill.name)") }
    if ($skillText -notmatch "(?m)^name: $([regex]::Escape($skill.name))\r?$") { $failures.Add("Skill name mismatch: $($skill.name)") }
    if ($skillText -notmatch '(?m)^description: .+') { $failures.Add("Missing description: $($skill.name)") }
}
foreach ($skillFile in Get-ChildItem -LiteralPath (Join-Path $projectRoot '.agents/skills') -Filter 'SKILL.md' -Recurse) {
    $relativePath = $skillFile.FullName.Substring($projectRoot.Length + 1).Replace('\', '/')
    if ($lock.skills.path -notcontains $relativePath) { $failures.Add("Unregistered skill: $relativePath") }
}

if ($failures.Count -gt 0) { throw ($failures -join [Environment]::NewLine) }
Write-Output "PASS: PowerShell syntax, $($documents.Count) documents, $($lock.skills.Count) registered skills and SHA-256."
