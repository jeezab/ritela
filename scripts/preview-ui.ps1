[CmdletBinding()]
param([switch]$SkipRender, [switch]$Open)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$projectRoot = Split-Path -Parent $PSScriptRoot

if (-not $SkipRender) {
    & (Join-Path $PSScriptRoot 'gradle.ps1') testDebugUnitTest --tests app.ritela.HomeScreenTest --rerun-tasks
}

$directory = Join-Path $projectRoot 'app/build/reports/screenshots'
$labels = [ordered]@{
    'home-light' = 'Главная · светлая тема'
    'home-dark' = 'Главная · тёмная тема'
    'home-active' = 'Месячные идут'
    'home-recorded' = 'Заполненная история'
    'home-active-dark' = 'Месячные идут · тёмная тема'
    'home-recorded-dark' = 'История · тёмная тема'
    'home-narrow-large-text' = 'Узкий экран · шрифт 200%'
    'period-entry' = 'Запись дат'
    'period-finish' = 'Выбор окончания'
}
$cards = foreach ($name in $labels.Keys) {
    $path = Join-Path $directory "$name.png"
    if (-not (Test-Path -LiteralPath $path)) { throw "Missing render: $path. Run preview-ui.ps1 without -SkipRender." }
    $label = [System.Net.WebUtility]::HtmlEncode($labels[$name])
    $rendered = (Get-Item -LiteralPath $path).LastWriteTime.ToString('yyyy-MM-dd HH:mm')
    "<figure><figcaption>$label <small>$rendered</small></figcaption><a href='$name.png'><img src='$name.png' alt='$label' loading='lazy'></a></figure>"
}
$html = @"
<!doctype html>
<html lang="ru"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Ritela · просмотр интерфейса</title>
<style>
body{margin:0;padding:32px;background:#f3f3ed;color:#202823;font:16px system-ui,sans-serif}
h1{font-size:28px;font-weight:600}p{max-width:760px;line-height:1.6;color:#535f57}
main{display:flex;flex-wrap:wrap;gap:32px;align-items:flex-start}
figure{margin:0;width:300px}figcaption{margin:0 0 12px;font-weight:600}small{display:block;font-weight:400;color:#535f57;font-size:12px;margin-top:4px}
img{width:100%;height:auto;border-radius:16px;box-shadow:0 4px 24px #20282314}
a:focus-visible{outline:3px solid #315d50} @media(max-width:380px){body{padding:16px}figure{width:100%}}
</style>
<h1>Ritela</h1><p>Снимки Android-интерфейса на тестовых данных. Нажмите на экран, чтобы открыть его в полном размере. Здесь нельзя нажимать кнопки приложения: для этого нужен эмулятор.</p>
<main>$($cards -join [Environment]::NewLine)</main></html>
"@
$output = Join-Path $directory 'index.html'
[IO.File]::WriteAllText($output, $html, [Text.UTF8Encoding]::new($false))
Write-Output "UI gallery: $output"
if ($Open) { Start-Process -FilePath $output }
