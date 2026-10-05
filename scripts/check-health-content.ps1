$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$projectRoot = Split-Path -Parent $PSScriptRoot
$library = Get-Content -Raw -Encoding UTF8 -LiteralPath (Join-Path $projectRoot 'app/src/main/kotlin/app/ritela/ui/HelpCards.kt')
$pattern = 'HelpArticle\(\s*"(?<id>[a-z-]+)",\s*R\.string\.(?<title>[a-z_]+),\s*R\.string\.(?<body>[a-z_]+),\s*"(?<url>https://[^"]+)"(?:,\s*R\.string\.(?<dose>[a-z_]+))?\s*\)'
$articles = [regex]::Matches($library, $pattern)
$declarations = [regex]::Matches($library, '(?m)^\s*HelpArticle\(').Count
if ($articles.Count -eq 0 -or $articles.Count -ne $declarations) { throw 'Could not inspect every health article declaration.' }
$resources = @{}
foreach ($locale in @('values', 'values-ru')) {
    $resources[$locale] = @{}
    foreach ($file in Get-ChildItem -LiteralPath (Join-Path $projectRoot "app/src/main/res/$locale") -Filter '*.xml') {
        [xml]$xml = Get-Content -Raw -Encoding UTF8 -LiteralPath $file.FullName
        foreach ($node in $xml.SelectNodes('/resources/string')) {
            $name = $node.GetAttribute('name')
            if ($resources[$locale].ContainsKey($name)) { throw "Duplicate resource: $locale/$name" }
            $resources[$locale][$name] = $node.InnerText
        }
    }
}
$seen = @{}
$doses = 0
foreach ($article in $articles) {
    $id = $article.Groups['id'].Value
    if ($seen.ContainsKey($id)) { throw "Duplicate health article: $id" }
    $seen[$id] = $true
    $uri = [Uri]$article.Groups['url'].Value
    if ($uri.Scheme -ne 'https' -or $uri.Host -ne 'www.nhs.uk') { throw "Review the source allowlist for article: $id" }
    foreach ($locale in $resources.Keys) {
        foreach ($field in @('title', 'body', 'dose')) {
            $name = $article.Groups[$field].Value
            if ($name -and (-not $resources[$locale].ContainsKey($name) -or -not $resources[$locale][$name])) {
                throw "Missing health copy: $locale/$id/$field"
            }
        }
        if ($article.Groups['dose'].Success -and $resources[$locale][$article.Groups['title'].Value] -notmatch '18\+') {
            throw "Missing adult title: $locale/$id"
        }
    }
    if ($article.Groups['dose'].Success) { $doses++ }
}
Write-Output "PASS: $($articles.Count) sourced EN/RU articles, unique IDs, HTTPS NHS links, $doses adult-dose entries. Clinical correctness still requires source review."
